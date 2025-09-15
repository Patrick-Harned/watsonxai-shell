package org.ibm.client.components.foundationmodels


import io.circe.{Decoder, Encoder, Json}

// Sealed trait to handle different parameter value types
sealed trait ModelParameterValue

object ModelParameterValue {
  case class StringValue(value: String) extends ModelParameterValue
  case class DoubleValue(value: Double) extends ModelParameterValue
  case class BooleanValue(value: Boolean) extends ModelParameterValue

  // Custom Circe codec for handling different value types
  implicit val decoder: Decoder[ModelParameterValue] = Decoder.instance { cursor =>
    cursor.as[String].map(StringValue.apply)
      .orElse(cursor.as[Double].map(DoubleValue.apply))
      .orElse(cursor.as[Boolean].map(BooleanValue.apply))
  }

  implicit val encoder: Encoder[ModelParameterValue] = Encoder.instance {
    case StringValue(value) => Json.fromString(value)
    case DoubleValue(value) => Json.fromDoubleOrNull(value)
    case BooleanValue(value) => Json.fromBoolean(value)
  }

  // Helper methods for extracting values
  def getString(value: ModelParameterValue): Option[String] = value match {
    case StringValue(s) => Some(s)
    case _ => None
  }

  def getDouble(value: ModelParameterValue): Option[Double] = value match {
    case DoubleValue(d) => Some(d)
    case StringValue(s) => s.toDoubleOption
    case _ => None
  }

  def getBoolean(value: ModelParameterValue): Option[Boolean] = value match {
    case BooleanValue(b) => Some(b)
    case StringValue(s) => s.toBooleanOption
    case _ => None
  }

  def asString(value: ModelParameterValue): String = value match {
    case StringValue(s) => s
    case DoubleValue(d) => d.toString
    case BooleanValue(b) => b.toString
  }

  def asJson(value: ModelParameterValue): Json = encoder(value)
}
