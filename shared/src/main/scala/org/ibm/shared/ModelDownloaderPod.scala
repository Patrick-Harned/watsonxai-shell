package org.ibm.shared


import io.circe.{Decoder, Encoder}
import io.circe.generic.semiauto._
import java.time.Instant

// Domain model for the model downloader pod
case class ModelDownloaderPod(
                               name: String,
                               pvcName: String,
                               modelRepo: String,
                               localDirName: String,
                               namespace: String = "cpd",
                               status: Option[PodStatus] = None,
                               createdAt: Option[Instant] = None
                             )

case class PodStatus(
                      phase: String, // Pending, Running, Succeeded, Failed, Unknown
                      message: Option[String] = None,
                      reason: Option[String] = None,
                      startTime: Option[Instant] = None,
                      completionTime: Option[Instant] = None
                    )

// Circe codecs
object ModelDownloaderPod {
  implicit val instantDecoder: Decoder[Instant] = Decoder.decodeString.map(Instant.parse)
  implicit val instantEncoder: Encoder[Instant] = Encoder.encodeString.contramap(_.toString)

  implicit val podStatusDecoder: Decoder[PodStatus] = deriveDecoder[PodStatus]
  implicit val podStatusEncoder: Encoder[PodStatus] = deriveEncoder[PodStatus]

  implicit val decoder: Decoder[ModelDownloaderPod] = deriveDecoder[ModelDownloaderPod]
  implicit val encoder: Encoder[ModelDownloaderPod] = deriveEncoder[ModelDownloaderPod]
}
// Add this to your ModelDownloaderPod companion object in org.ibm.shared
object CreateModelDownloaderRequest {
  implicit val decoder: Decoder[CreateModelDownloaderRequest] = deriveDecoder[CreateModelDownloaderRequest]
  implicit val encoder: Encoder[CreateModelDownloaderRequest] = deriveEncoder[CreateModelDownloaderRequest]
}

case class CreateModelDownloaderRequest(
                                         pvcName: String,
                                         modelRepo: String,
                                         localDirName: String,
                                         namespace: String = "cpd"
                                       )
