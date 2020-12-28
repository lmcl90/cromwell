package cromwell.backend.impl.sfs.k8s

import cromwell.backend.BackendConfigurationDescriptor
import cromwell.backend.sfs.SharedFileSystemBackendLifecycleActorFactory
import cromwell.backend.standard.{StandardAsyncExecutionActor, StandardInitializationActor}


class K8sBackendLifecycleActorFactory(val name: String, val configurationDescriptor: BackendConfigurationDescriptor)
  extends SharedFileSystemBackendLifecycleActorFactory {

  override lazy val asyncExecutionActorClass: Class[_ <: StandardAsyncExecutionActor] = classOf[K8sAsyncJobExecutionActor]

  override lazy val initializationActorClass: Class[_ <: StandardInitializationActor] = classOf[K8sInitializationActor]
}
