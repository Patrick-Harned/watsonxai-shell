package org.ibm.shared

import io.circe.{Decoder, Encoder}
import io.circe.generic.semiauto.{deriveDecoder, deriveEncoder}

// In org.ibm.shared
case class ConsoleInfo(url: String)

object ConsoleInfo {
  implicit val decoder: Decoder[ConsoleInfo] = deriveDecoder[ConsoleInfo]
  implicit val encoder: Encoder[ConsoleInfo] = deriveEncoder[ConsoleInfo]
}
