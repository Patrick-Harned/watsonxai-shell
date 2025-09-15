package org.ibm.shared



import io.circe.{Decoder, Encoder, Json}
import io.circe.generic.semiauto._

// StorageClass Domain Model
case class StorageClassMetadata(
                                 name: String,
                                 uid: Option[String] = None,
                                 resourceVersion: Option[String] = None,
                                 creationTimestamp: Option[String] = None,
                                 annotations: Option[Map[String, String]] = None
                               )

case class StorageClass(
                         kind: String = "StorageClass",
                         apiVersion: String = "storage.k8s.io/v1",
                         metadata: StorageClassMetadata,
provisioner: String,
parameters: Option[Map[String, String]] = None,
reclaimPolicy: Option[String] = None,
allowVolumeExpansion: Option[Boolean] = None,
volumeBindingMode: Option[String] = None
)

// PVC Domain Models
case class PVCMetadata(
                        name: String,
                        namespace: String,
                        uid: Option[String] = None,
                        resourceVersion: Option[String] = None,
                        creationTimestamp: Option[String] = None,
                        annotations: Option[Map[String, String]] = None,
                        labels: Option[Map[String, String]] = None,
                        finalizers: Option[List[String]] = None
                      )

case class PVCResourceRequests(
                                storage: String
                              )

case class PVCResources(
                         requests: PVCResourceRequests
                       )

case class PVCSpec(
                    accessModes: List[String],
                    resources: PVCResources,
                    storageClassName: String,
                    volumeMode: Option[String] = Some("Filesystem"),
                    volumeName: Option[String] = None
                  )

case class PVCStatus(
                      phase: Option[String] = None,
                      accessModes: Option[List[String]] = None,
                      capacity: Option[Map[String, String]] = None
                    )

case class PVC(
                kind: String = "PersistentVolumeClaim",
                apiVersion: String = "v1",
                metadata: PVCMetadata,
spec: PVCSpec,
status: Option[PVCStatus] = None
)

// Request models for creating PVCs
case class CreatePVCRequest(
                             name: String,
                             storageClassName: String,
                             size: String,
                             accessModes: List[String] = List("ReadWriteOnce"),
                             labels: Option[Map[String, String]] = None
                           )
object CreatePVCRequest {
  implicit val decode: Decoder[CreatePVCRequest] = deriveDecoder
  implicit val encode: Encoder[CreatePVCRequest] = deriveEncoder
}

object StorageClass {
  implicit val metadataDecoder: Decoder[StorageClassMetadata] = deriveDecoder
  implicit val metadataEncoder: Encoder[StorageClassMetadata] = deriveEncoder

  implicit val decoder: Decoder[StorageClass] = deriveDecoder
  implicit val encoder: Encoder[StorageClass] = deriveEncoder
}

object PVC {
  implicit val requestsDecoder: Decoder[PVCResourceRequests] = deriveDecoder
  implicit val requestsEncoder: Encoder[PVCResourceRequests] = deriveEncoder

  implicit val resourcesDecoder: Decoder[PVCResources] = deriveDecoder
  implicit val resourcesEncoder: Encoder[PVCResources] = deriveEncoder

  implicit val specDecoder: Decoder[PVCSpec] = deriveDecoder
  implicit val specEncoder: Encoder[PVCSpec] = deriveEncoder

  implicit val statusDecoder: Decoder[PVCStatus] = deriveDecoder
  implicit val statusEncoder: Encoder[PVCStatus] = deriveEncoder

  implicit val metadataDecoder: Decoder[PVCMetadata] = deriveDecoder
  implicit val metadataEncoder: Encoder[PVCMetadata] = deriveEncoder

  implicit val decoder: Decoder[PVC] = deriveDecoder
  implicit val encoder: Encoder[PVC] = deriveEncoder

  implicit val createRequestDecoder: Decoder[CreatePVCRequest] = deriveDecoder
  implicit val createRequestEncoder: Encoder[CreatePVCRequest] = deriveEncoder
}
