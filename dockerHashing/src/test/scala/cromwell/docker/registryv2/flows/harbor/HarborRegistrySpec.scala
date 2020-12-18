package cromwell.docker.registryv2.flows.harbor

import cromwell.core.TestKitSuite
import cromwell.docker.{DockerImageIdentifier, DockerRegistryConfig}
import org.scalatest.flatspec.AnyFlatSpecLike
import org.scalatest.matchers.should.Matchers

class HarborRegistrySpec extends TestKitSuite with AnyFlatSpecLike with Matchers {
  behavior of "HarborRegistry"

  val harborRegistry = new HarborRegistry(DockerRegistryConfig.default)

  it should ("accept med internal image") in {
    harborRegistry.accepts(DockerImageIdentifier.fromString("harbor.yitu-med.info/gene/gene-tools").get) shouldEqual true
  }

  it should ("return a right uri for getting token") in {
    val uri = harborRegistry.buildTokenRequestUri(
      DockerImageIdentifier.fromString("harbor.yitu-med.info/gene/gene-tools:1.4.0").get
    )
    uri.path shouldEqual "/service/token"
    uri.query.toString.replaceAll("%3A", ":") shouldEqual "service=harbor-registry&scope=repository:gene/gene-tools:pull"
  }
}
