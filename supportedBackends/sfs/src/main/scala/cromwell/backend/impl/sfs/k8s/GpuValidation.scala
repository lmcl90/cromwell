package cromwell.backend.impl.sfs.k8s

import cromwell.backend.validation.{CpuValidation, OptionalRuntimeAttributesValidation, RuntimeAttributesValidation}
import eu.timepit.refined.api.Refined
import eu.timepit.refined.numeric.Positive
import wom.RuntimeAttributesKeys.GpuKey

object GpuValidation {
  lazy val instance: RuntimeAttributesValidation[Int Refined Positive] = new CpuValidation(GpuKey)
  lazy val optional: OptionalRuntimeAttributesValidation[Int Refined Positive] = instance.optional
}
