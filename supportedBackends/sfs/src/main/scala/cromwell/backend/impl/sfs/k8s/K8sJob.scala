package cromwell.backend.impl.sfs.k8s

import com.google.gson.JsonSyntaxException
import io.kubernetes.client.custom.Quantity
import io.kubernetes.client.openapi.ApiException
import io.kubernetes.client.openapi.apis.BatchV1Api
import io.kubernetes.client.openapi.models.{V1JobBuilder, V1PersistentVolumeClaimVolumeSource, V1ResourceRequirements}
import io.kubernetes.client.util.Yaml

import scala.collection.JavaConverters.{asJavaCollection, mapAsJavaMap}
import scala.util.{Failure, Success, Try}

final case class K8sJob(name: String,
                        labels: Map[String, String],
                        image: String,
                        commands: List[String],
                        cpu: String,
                        gpu: Option[String],
                        memory: String,
                        pvcName: String,
                        mountPath: String,
                        subPath: String,
                        k8sApi: BatchV1Api,
                        namespace: String = "default",
                        backoffLimit: Int = 1
                       ) {
  private val volumeName = "data-volume"
  private val body = {
    val resources = new V1ResourceRequirements()
    resources.putLimitsItem("cpu", Quantity.fromString(cpu))
      .putLimitsItem("memory", Quantity.fromString(memory))

    gpu match {
      case Some(v) => resources.putLimitsItem("nvidia.com/gpu", Quantity.fromString(v))
      case _ => ()
    }

    new V1JobBuilder()
      .withApiVersion("batch/v1")
      .withKind("Job")
      // job meta
      .withNewMetadata()
      .withName(name)
      .withNamespace(namespace)
      .withLabels(mapAsJavaMap(labels))
      .endMetadata()
      // job spec
      .withNewSpec()
      .withBackoffLimit(backoffLimit)
      // pod template
      .withNewTemplate()
      // pod spec
      .withNewSpec()
      .addNewContainer()
      .withName(name)
      .withImage(image)
      .addAllToCommand(asJavaCollection(commands))
      // container resources
      .withResources(resources)
      // container volume mount
      .addNewVolumeMount()
      .withName(volumeName)
      .withSubPath(subPath)
      .withMountPath(mountPath)
      .endVolumeMount()
      .endContainer()
      // pod volumes
      .addNewVolume()
      .withName(volumeName)
      .withPersistentVolumeClaim(
        new V1PersistentVolumeClaimVolumeSource()
          .claimName(pvcName)
      )
      .endVolume()
      .withRestartPolicy("Never")
      // end of  pod spec
      .endSpec()
      // end of pod template
      .endTemplate()
      // end of job spec
      .endSpec()
      .build()
  }

  def delete(): Unit = {
    Try {
      k8sApi.deleteNamespacedJob(
        name,
        namespace,
        "true",
        null,
        0,
        null,
        "Foreground",
        null
      )
    } match {
      case Success(_) => ()
      case Failure(exception: ApiException) if 400 <= exception.getCode && exception.getCode < 500 => ()
      // delete object api may throw json syntax exception when deserializing response
      // even though the object was deleted successfully
      // see https://github.com/kubernetes-client/java/issues/86
      case Failure(exception: JsonSyntaxException) if exception.getMessage.contains("Expected a string but was BEGIN_OBJECT") => ()
      case Failure(exception) => throw exception
    }
  }

  def getStatus: RunStatus = {
    val status = k8sApi.readNamespacedJobStatus(name, namespace, "true").getStatus

    if (status.getSucceeded > 0) {
      new JobSucceeded
    } else if (status.getFailed > 0) {
      new JobFailed
    } else {
      new JobRunning
    }
  }

  def submit(dryRun: String = null): Unit = {
    k8sApi.createNamespacedJob(namespace, body, null, dryRun, null)
    ()
  }

  def toYaml: String = Yaml.dump(body)

}
