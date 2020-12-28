package cromwell.backend.impl.sfs.k8s

import cats.syntax.validated._
import common.validation.ErrorOr.ErrorOr
import cromwell.backend.validation.{OptionalRuntimeAttributesValidation, RuntimeAttributesValidation, StringRuntimeAttributesValidation}
import wom.RuntimeAttributesKeys.GpuTypeKey
import wom.values.{WomString, WomValue}

object GpuTypeValidation {
  lazy val instance: RuntimeAttributesValidation[String] = new GpuTypeValidation
  lazy val optional: OptionalRuntimeAttributesValidation[String] = instance.optional
}

class GpuTypeValidation extends StringRuntimeAttributesValidation(GpuTypeKey) {

  override protected def missingValueMessage: String = s"Can't find an attribute value for key ${GpuTypeKey}"

  override protected def invalidValueMessage(value: WomValue): String = super.missingValueMessage

  override protected def validateValue: PartialFunction[WomValue, ErrorOr[String]] = {
    case WomString(value) => value.validNel
  }
}
