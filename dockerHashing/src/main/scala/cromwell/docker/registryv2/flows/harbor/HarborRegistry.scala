package cromwell.docker.registryv2.flows.harbor

import cromwell.docker.DockerInfoActor.DockerInfoContext
import cromwell.docker.registryv2.DockerRegistryV2Abstract
import cromwell.docker.{DockerImageIdentifier, DockerRegistryConfig}
import org.http4s.Uri.{Authority, Scheme}
import org.http4s.{Header, Query, Uri}

class HarborRegistry(config: DockerRegistryConfig) extends DockerRegistryV2Abstract(config) {

  private val hostName = "harbor.yitu-med.info"

  override def registryHostName(dockerImageIdentifier: DockerImageIdentifier): String = hostName

  override def authorizationServerHostName(dockerImageIdentifier: DockerImageIdentifier): String = hostName

  override val serviceName: Option[String] = Option("harbor-registry")

  override def accepts(dockerImageIdentifier: DockerImageIdentifier): Boolean = {
    dockerImageIdentifier.host match {
      case Some(h) if hostName.equals(h) => true
      case _ => false
    }
  }

  // harbor allows anonymous access to get a token
  override def buildTokenRequestHeaders(dockerInfoContext: DockerInfoContext): List[Header] = List.empty;

  override def buildTokenRequestUri(dockerImageID: DockerImageIdentifier): Uri = {
    val service = serviceName map { name => s"service=$name&" } getOrElse ""
    Uri.apply(
      scheme = Option(Scheme.https),
      authority = Option(Authority(host = Uri.RegName(authorizationServerHostName(dockerImageID)))),
      path = "/service/token",
      query = Query.fromString(s"${service}scope=repository:${dockerImageID.nameWithDefaultRepository}:pull")
    )
  }
}
