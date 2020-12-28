package cromwell.backend.impl.sfs.k8s

sealed trait RunStatus {
  def status: String

  def terminal: Boolean

  override def toString: String = status
}

case class JobFailed() extends RunStatus {

  override def status: String = "Failed"

  override def terminal: Boolean = true
}

case class JobRunning() extends RunStatus {

  override def status: String = "Running"

  override def terminal: Boolean = false
}

case class JobSucceeded() extends RunStatus {

  override def status: String = "Succeeded"

  override def terminal: Boolean = true
}

