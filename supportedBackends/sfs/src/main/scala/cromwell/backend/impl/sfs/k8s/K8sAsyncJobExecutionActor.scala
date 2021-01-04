package cromwell.backend.impl.sfs.k8s

import io.kubernetes.client.openapi.ApiException
import cromwell.backend.BackendJobLifecycleActor
import cromwell.backend.async.{ExecutionHandle, FailedNonRetryableExecutionHandle, PendingExecutionHandle}
import cromwell.backend.io.JobPathsWithDocker
import cromwell.backend.sfs.SharedFileSystemJobCachingActorHelper
import cromwell.backend.standard.{StandardAsyncExecutionActor, StandardAsyncExecutionActorParams, StandardAsyncJob}
import cromwell.backend.validation.RuntimeAttributesValidation
import cromwell.core.path.{DefaultPathBuilder, Path}
import cromwell.core.retry.SimpleExponentialBackoff
import eu.timepit.refined.api.Refined
import eu.timepit.refined.numeric.Positive
import wom.RuntimeAttributesKeys
import wom.format.MemorySize
import wom.values.WomFile

import scala.concurrent.duration._
import scala.util.{Failure, Success, Try}

class K8sAsyncJobExecutionActor(override val standardParams: StandardAsyncExecutionActorParams)
  extends BackendJobLifecycleActor with StandardAsyncExecutionActor with SharedFileSystemJobCachingActorHelper {
  override type StandardAsyncRunInfo = K8sJob
  override type StandardAsyncRunState = RunStatus

  lazy val initializationData: K8sInitializationData = backendInitializationDataAs[K8sInitializationData]

  override def statusEquivalentTo(thiz: StandardAsyncRunState)(that: StandardAsyncRunState): Boolean =
    thiz == that

  override lazy val pollBackOff: SimpleExponentialBackoff =
    SimpleExponentialBackoff(1.second, 5.minutes, 1.1)

  override lazy val executeOrRecoverBackOff: SimpleExponentialBackoff =
    SimpleExponentialBackoff(3.seconds, 30.seconds, 1.1)

  lazy val jobPathsWithDocker: JobPathsWithDocker = jobPaths.asInstanceOf[JobPathsWithDocker]

  private[k8s] lazy val jobName: String =
    List("cromwell",
      jobDescriptor.workflowDescriptor.id.shortString,
      jobDescriptor.taskCall.localName.toLowerCase.replace("_", "-")
    ).mkString("-")

  override lazy val dockerImageUsed: Option[String] = Option(
    validatedRuntimeAttributes.attributes.get(RuntimeAttributesKeys.DockerKey).asInstanceOf[Some[String]].get
  )

  lazy val cpuUsed: String = RuntimeAttributesValidation.extract[Int Refined Positive](RuntimeAttributesKeys.CpuKey, validatedRuntimeAttributes).value.toString
  lazy val memoryUsed: String = {
    val memorySize = RuntimeAttributesValidation.extract[MemorySize](RuntimeAttributesKeys.MemoryKey, validatedRuntimeAttributes)
    s"${memorySize.amount}${memorySize.unit.suffixes(memorySize.unit.suffixes.length - 1)}"
  }

  lazy val gpuUsed: Option[String] = Try {
    RuntimeAttributesValidation.extract[Int Refined Positive](RuntimeAttributesKeys.GpuKey, validatedRuntimeAttributes).value
  } match {
    case Success(v) => Option(v.toString)
    case Failure(_) => Option(null)
  }

  override lazy val isDockerRun: Boolean = true

  /**
    * Localizes the file, run outside of docker.
    */
  override def preProcessWomFile(womFile: WomFile): WomFile = {
    sharedFileSystem.localizeWomFile(jobPathsWithDocker.callInputsRoot, isDockerRun)(womFile)
  }

  /**
    * Returns the paths to the file, inside of docker.
    */
  override def mapCommandLineWomFile(womFile: WomFile): WomFile = {
    womFile mapFile { path =>
      val cleanPath = DefaultPathBuilder.build(path).get
      jobPathsWithDocker.toDockerPath(cleanPath).pathAsString
    }
  }

  override def mapOutputWomFile(womFile: WomFile): WomFile = {
    sharedFileSystem.mapJobWomFile(jobPaths)(womFile)
  }

  override lazy val commandDirectory: Path =
    jobPathsWithDocker.callExecutionDockerRoot


  private lazy val job = K8sJob(
    jobName,
    initializationData.k8sConfiguration.defaultLabels,
    dockerImageUsed.get,
    List(jobShell, jobPathsWithDocker.toDockerPath(jobPaths.script).pathAsString),
    cpuUsed,
    gpuUsed,
    memoryUsed,
    initializationData.k8sConfiguration.pvcName,
    jobPathsWithDocker.callDockerRoot.pathAsString,
    jobPathsWithDocker.callDockerRoot.pathAsString.substring(1),
    initializationData.k8sConfiguration.k8sClient,
    initializationData.k8sConfiguration.namespace,
    initializationData.k8sConfiguration.backoffLimit
  )

  override def execute(): ExecutionHandle = {
    jobPaths.callExecutionRoot.createPermissionedDirectories()
    commandScriptContents.fold(
      errors => FailedNonRetryableExecutionHandle(new RuntimeException("Unable to start job due to: " + errors.toList.mkString(", ")), kvPairsToSave = None),
      script => jobPaths.script.write(script))

    jobPaths.callExecutionRoot.resolve("job.yaml").write(job.toYaml)

    Try {
      job.submit()
    } match {
      case Success(_) => PendingExecutionHandle(jobDescriptor, StandardAsyncJob(jobName), Option(job), None)
      case Failure(exception: ApiException) => FailedNonRetryableExecutionHandle(new RuntimeException(exception.getResponseBody, exception), kvPairsToSave = None)
      case Failure(exception) => FailedNonRetryableExecutionHandle(new RuntimeException(exception.getMessage, exception), kvPairsToSave = None)
    }
  }

  override def isDone(runStatus: RunStatus): Boolean = {
    runStatus match {
      case _: JobSucceeded | _: JobFailed =>
        // todo 可以传入参考控制是否删除job
        Try {
          job.delete()
        } match {
          case Failure(exception: ApiException) =>
            jobLogger.warn(exception.getResponseBody)
          case Failure(exception) =>
            jobLogger.warn(exception.getMessage)
          case _ => ()
        }
        true
      case _ => false
    }
  }

  /**
    * Returns true when a job is complete, either successfully or unsuccessfully.
    *
    * @param runStatus The run status.
    * @return True if the job has completed.
    */
  override def isTerminal(runStatus: RunStatus): Boolean = runStatus.terminal

  override def pollStatus(handle: StandardAsyncPendingExecutionHandle): RunStatus = {
    val k8sJob = handle.runInfo.getOrElse(throw new RuntimeException("empty run job info"))
    val status = k8sJob.getStatus
    jobLogger.debug(s"job: ${k8sJob.name} polled status: $status")
    status
  }

  override def tryAbort(jobId: StandardAsyncJob): Unit = {
    job.delete()
    ()
  }

  override def isFatal(throwable: Throwable): Boolean = throwable match {
    case _: ApiException => true
    case _ => super.isFatal(throwable)
  }
}
