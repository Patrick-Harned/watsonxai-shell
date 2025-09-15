package org.ibm.shared

import io.circe.{Decoder, Encoder, Json}
import io.circe.generic.semiauto._

case class WatsonxMetadata(
                            name: String,
                            namespace: String,
                            creationTimestamp: String,
                            resourceVersion: String,
                            uid: String,
                            generation: Option[Double] = None,
                            labels: Map[String, String] = Map.empty
                          )

case class WatsonxLicense(
                           accept: Boolean
                         )

case class WatsonxSpec(
                        version: String,
                        size: String,
                        blockStorageClass: String,
                        fileStorageClass: String,
                        storageClass: String,
                        docker_registry_prefix: String,
                        ignoreForMaintenance: Boolean,
                        license: WatsonxLicense,
                        custom_foundation_models: List[CustomFoundationModel] = List.empty,
                        install_model_list: List[String] = List.empty
                      )

case class CustomFoundationModel(
                                  model_id: String,
                                  location: ModelLocation,
                                  tags: List[String] = List.empty,
                                  parameters: List[ModelParameter] = List.empty
                                )

case class ModelLocation(
                          pvc_name: String,
                          sub_path: String
                        )

case class ModelParameter(
                           name: String,
                           default: Json, // Use Json for dynamic values
                           options: Option[List[String]] = None,
                           min: Option[Double] = None,
                           max: Option[Double] = None
                         )

case class WatsonxVersions(
                            reconciled: String
                          )

case class AnsibleResult(
                          changed: Double,
                          completion: String,
                          failures: Double,
                          ok: Double,
                          skipped: Double
                        )

case class StatusCondition(
                            `type`: String,
                            status: String,
                            reason: String,
                            message: String,
                            lastTransitionTime: String,
                            ansibleResult: Option[AnsibleResult] = None
                          )

case class WatsonxStatus(
                          watsonxaiifmStatus: String,
                          progress: String,
                          progressMessage: String,
                          `type`: String,
                          watsonxaiifmBuildNumber: Option[Double] = None,
                          versions: WatsonxVersions,
                          conditions: List[StatusCondition] = List.empty
                        )

case class WatsonxAIIFM(
                         apiVersion: String,
                         kind: String,
                         metadata: WatsonxMetadata,
                         spec: WatsonxSpec,
                         status: WatsonxStatus
                       )

object WatsonxAIIFM {
  implicit val licenseDecoder: Decoder[WatsonxLicense] = deriveDecoder
  implicit val licenseEncoder: Encoder[WatsonxLicense] = deriveEncoder

  implicit val locationDecoder: Decoder[ModelLocation] = deriveDecoder
  implicit val locationEncoder: Encoder[ModelLocation] = deriveEncoder

  implicit val parameterDecoder: Decoder[ModelParameter] = deriveDecoder
  implicit val parameterEncoder: Encoder[ModelParameter] = deriveEncoder

  implicit val modelDecoder: Decoder[CustomFoundationModel] = deriveDecoder
  implicit val modelEncoder: Encoder[CustomFoundationModel] = deriveEncoder

  implicit val metadataDecoder: Decoder[WatsonxMetadata] = deriveDecoder
  implicit val metadataEncoder: Encoder[WatsonxMetadata] = deriveEncoder

  implicit val versionsDecoder: Decoder[WatsonxVersions] = deriveDecoder
  implicit val versionsEncoder: Encoder[WatsonxVersions] = deriveEncoder

  implicit val ansibleResultDecoder: Decoder[AnsibleResult] = deriveDecoder
  implicit val ansibleResultEncoder: Encoder[AnsibleResult] = deriveEncoder

  implicit val conditionDecoder: Decoder[StatusCondition] = deriveDecoder
  implicit val conditionEncoder: Encoder[StatusCondition] = deriveEncoder

  implicit val statusDecoder: Decoder[WatsonxStatus] = deriveDecoder
  implicit val statusEncoder: Encoder[WatsonxStatus] = deriveEncoder

  implicit val specDecoder: Decoder[WatsonxSpec] = deriveDecoder
  implicit val specEncoder: Encoder[WatsonxSpec] = deriveEncoder

  implicit val decoder: Decoder[WatsonxAIIFM] = deriveDecoder
  implicit val encoder: Encoder[WatsonxAIIFM] = deriveEncoder
}
object CustomFoundationModel {
  implicit val modelDecoder: Decoder[CustomFoundationModel] = deriveDecoder
  implicit val modelEncoder: Encoder[CustomFoundationModel] = deriveEncoder

  implicit val locationDecoder: Decoder[ModelLocation] = deriveDecoder
  implicit val locationEncoder: Encoder[ModelLocation] = deriveEncoder
  implicit val parameterDecoder: Decoder[ModelParameter] = deriveDecoder
  implicit val parameterEncoder: Encoder[ModelParameter] = deriveEncoder

}