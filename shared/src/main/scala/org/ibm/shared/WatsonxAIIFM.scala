package org.ibm.shared

import io.circe.{Decoder, Encoder}
import io.circe.generic.semiauto._

case class WatsonxAIIFM(cr: String)

object WatsonxAIIFM {
  implicit val decode: Decoder[WatsonxAIIFM] = deriveDecoder
  implicit val encode: Encoder[WatsonxAIIFM] = deriveEncoder
}
