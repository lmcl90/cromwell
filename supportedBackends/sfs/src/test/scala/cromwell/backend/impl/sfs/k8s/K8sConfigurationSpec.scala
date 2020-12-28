package cromwell.backend.impl.sfs.k8s

import com.typesafe.config.{Config, ConfigFactory}
import cromwell.backend.BackendConfigurationDescriptor
import org.scalatest.flatspec.AnyFlatSpecLike
import org.scalatest.matchers.should.Matchers

object K8sConfigurationSpec {

  val K8sBackendConfigString: String =
    s"""
       |root = "/data/cromwell-executions"
       |dockerRoot = "/cromwell-executions"
       |
       |namespace = "gene"
       |backoff-limit = 1
       |pvc-name = "cromwell-pvc"
       |
       |""".stripMargin

  val GlobalBackendConfigString: String =
    s"""
       |backend {
       |  default = "K8s"
       |  providers {
       |    K8s {
       |      actor-factory = "cromwell.backend.impl.sfs.k8s.K8sBackendLifecycleActorFactory"
       |      config {
       |      $K8sBackendConfigString
       |      }
       |    }
       |  }
       |}
       |
       |""".stripMargin

  val k8sBackendConfig: Config = ConfigFactory.parseString(K8sBackendConfigString)
  val globalConfig: Config = ConfigFactory.parseString(GlobalBackendConfigString)

  val backendConfigurationDescriptor: BackendConfigurationDescriptor = BackendConfigurationDescriptor(k8sBackendConfig, globalConfig)
}

class K8sConfigurationSpec extends AnyFlatSpecLike with Matchers {

  import K8sConfigurationSpec.backendConfigurationDescriptor

  val k8sConfiguration = new K8sConfiguration(backendConfigurationDescriptor)

  it should "have empty default labels" in {
    k8sConfiguration.defaultLabels should be(empty)
  }

}
