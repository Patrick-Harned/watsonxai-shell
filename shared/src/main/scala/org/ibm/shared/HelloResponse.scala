package org.ibm.shared

import io.circe.generic.semiauto.*
import io.circe.{Decoder, Encoder}

case class HelloResponse(message: String)

object HelloResponse {
  implicit val decoder: Decoder[HelloResponse] = deriveDecoder
  implicit val encoder: Encoder[HelloResponse] = deriveEncoder
}
