package org.ibm.shared


import io.circe.{Decoder, Encoder}
import io.circe.generic.semiauto.{deriveDecoder, deriveEncoder}

import java.time.Instant

case class DownloadedModel(
                            id: String, // A unique ID for this downloaded model entry
                            modelRepo: String,
                            localDirName: String,
                            pvcName: String, // The PVC it was downloaded to
                            downloadTime: Instant,
                            status: String, // e.g., "PENDING", "COMPLETED", "FAILED"
                            downloadPodName: Option[String] = None // Optional: the name of the pod that performed the download
                          )
object DownloadedModel {
  implicit val decoder: Decoder[DownloadedModel] =deriveDecoder
implicit val encoder: Encoder[DownloadedModel] = deriveEncoder
}