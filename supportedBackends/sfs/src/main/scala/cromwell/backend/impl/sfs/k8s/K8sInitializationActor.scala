package cromwell.backend.impl.sfs.k8s

import cromwell.backend.io.WorkflowPaths
import cromwell.backend.sfs.{SharedFileSystemExpressionFunctions, SharedFileSystemInitializationActor}
import cromwell.backend.standard.{StandardInitializationActorParams, StandardInitializationData, StandardValidatedRuntimeAttributesBuilder}
import cromwell.backend.validation.{CpuValidation, DockerValidation, MemoryValidation}

import scala.concurrent.Future

final case class K8sInitializationData
(
  override val workflowPaths: WorkflowPaths,
  override val runtimeAttributesBuilder: StandardValidatedRuntimeAttributesBuilder,
  k8sConfiguration: K8sConfiguration
) extends StandardInitializationData(
  workflowPaths,
  runtimeAttributesBuilder,
  classOf[SharedFileSystemExpressionFunctions]
)

class K8sInitializationActor(params: StandardInitializationActorParams)
  extends SharedFileSystemInitializationActor(params) {

  override lazy val initializationData: Future[K8sInitializationData] = {
    workflowPaths map {
      K8sInitializationData(_, runtimeAttributesBuilder, new K8sConfiguration(params.configurationDescriptor))
    }
  }

  override lazy val runtimeAttributesBuilder: StandardValidatedRuntimeAttributesBuilder = {
    super.runtimeAttributesBuilder.withValidation(
      CpuValidation.instance,
      MemoryValidation.instance(),
      DockerValidation.instance,
      GpuValidation.optional,
      GpuTypeValidation.optional
    )
  }
}
