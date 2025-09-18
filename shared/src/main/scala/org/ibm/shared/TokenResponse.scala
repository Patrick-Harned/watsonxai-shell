package org.ibm.shared

import io.circe.generic.semiauto.{deriveDecoder, deriveEncoder}
import io.circe.{Decoder, Encoder}

case class TokenResponse(token: String) // Ensure this is defined or imported
object TokenResponse {
  implicit val encoder: Encoder[TokenResponse] = deriveEncoder
  implicit val decoder: Decoder[TokenResponse] = deriveDecoder
}