package cromwell.backend.impl.sfs.k8s

import io.kubernetes.client.openapi.apis.BatchV1Api
import io.kubernetes.client.util.Config
import org.scalatest.flatspec.AnyFlatSpecLike
import org.scalatest.matchers.should.Matchers

class K8sJobSpec extends AnyFlatSpecLike with Matchers {
  behavior of "K8sJob"

  private val job = K8sJob(
    "test-job",
    Map("env" -> "unit-test"),
    "harbor.yitu-med.info/gene/gene-tools:1.4.0",
    List("/bin/bash", "echo Hello World"),
    "1",
    Option(null),
    "1024Mi",
    "pvc",
    "/data",
    "data",
    new BatchV1Api(Config.defaultClient()),
  )

  //  it should "submit job successfully" in {
  //    job.submit("All").get shouldBe (())
  //  }


  it should "output a valid yaml representation of batch job" in {
    val yaml = job.toYaml
    yaml should not be ""
  }

  it should "not throw exception" in {
    job.submit()
    job.delete()
  }

}
