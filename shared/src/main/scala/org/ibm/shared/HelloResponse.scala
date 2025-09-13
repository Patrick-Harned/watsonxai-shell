package org.ibm.shared


import io.circe.{Decoder, Encoder}
import io.circe.generic.semiauto._

case class HelloResponse(message: String)

object HelloResponse {
  implicit val decoder: Decoder[HelloResponse] = deriveDecoder
  implicit val encoder: Encoder[HelloResponse] = deriveEncoder
}
