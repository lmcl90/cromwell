package cromwell.backend.impl.sfs.k8s

import com.typesafe.config.Config
import cromwell.backend.BackendConfigurationDescriptor
import io.kubernetes.client.openapi.apis.BatchV1Api
import net.ceedubs.ficus.Ficus._

final class K8sConfiguration
(
  private val backendConfigurationDescriptor: BackendConfigurationDescriptor
) {

  private val backendConfig = backendConfigurationDescriptor.backendConfig

  val namespace: String = backendConfig.as[Option[String]]("namespace") getOrElse "default"
  val backoffLimit: Int = backendConfig.as[Option[Int]]("backoff-limit") getOrElse 1
  val pvcName: String = backendConfig.getString("pvc-name")

  val defaultLabels: Map[String, String] = backendConfig.as[Option[List[Config]]]("default-labels").getOrElse(List.empty).map(
    c => c.getString("key") -> c.getString("value")
  ).toMap

  val k8sClient = new BatchV1Api(io.kubernetes.client.util.Config.defaultClient())
}
