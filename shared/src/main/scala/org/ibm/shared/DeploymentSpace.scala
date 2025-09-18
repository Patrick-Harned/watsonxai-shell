package org.ibm.shared
import io.circe.{Decoder, Encoder}
import io.circe.generic.semiauto.{deriveDecoder, deriveEncoder}

import java.time.Instant

case class ComputeResource(
                            crn: String,
                            guid: String,
                            name: String,
                            `type`: String
                          )
object ComputeResource {
  implicit val encoder: Encoder[ComputeResource] = deriveEncoder
  implicit val decoder: Decoder[ComputeResource] = deriveDecoder
}

case class Scope(bss_account_id: String)
object Scope {
  implicit val encoder: Encoder[Scope] = deriveEncoder
  implicit val decoder: Decoder[Scope] = deriveDecoder
}

case class AccessRestrictionsReporting(authorized: Boolean)
object AccessRestrictionsReporting {
  implicit val encoder: Encoder[AccessRestrictionsReporting] = deriveEncoder
  implicit val decoder: Decoder[AccessRestrictionsReporting] = deriveDecoder
}

case class AccessRestrictions(reporting: AccessRestrictionsReporting)
object AccessRestrictions {
  implicit val encoder: Encoder[AccessRestrictions] = deriveEncoder
  implicit val decoder: Decoder[AccessRestrictions] = deriveDecoder
}

case class Settings(access_restrictions: AccessRestrictions)
object Settings {
  implicit val encoder: Encoder[Settings] = deriveEncoder
  implicit val decoder: Decoder[Settings] = deriveDecoder
}

case class Stage(production: Boolean)
object Stage {
  implicit val encoder: Encoder[Stage] = deriveEncoder
  implicit val decoder: Decoder[Stage] = deriveDecoder
}

case class Status(state: String)
object Status {
  implicit val encoder: Encoder[Status] = deriveEncoder
  implicit val decoder: Decoder[Status] = deriveDecoder
}

case class DeploymentSpaceEntity(
                                  compute: Option[List[ComputeResource]],
                                  description: Option[String],
                                  name: String,
                                  scope: Option[Scope],
                                  settings: Option[Settings],
                                  stage: Option[Stage],
                                  status: Option[Status],
                                  `type`: String
                                )
object DeploymentSpaceEntity {
  implicit val encoder: Encoder[DeploymentSpaceEntity] = deriveEncoder
  implicit val decoder: Decoder[DeploymentSpaceEntity] = deriveDecoder
}

case class DeploymentSpaceMetadata(
                                    created_at: Instant,
                                    creator_id: String,
                                    id: String,
                                    updated_at: Instant,
                                    url: String
                                  )
object DeploymentSpaceMetadata {
  implicit val encoder: Encoder[DeploymentSpaceMetadata] = deriveEncoder
  implicit val decoder: Decoder[DeploymentSpaceMetadata] = deriveDecoder
}

case class DeploymentSpaceResource(
                                    entity: DeploymentSpaceEntity,
                                    metadata: DeploymentSpaceMetadata
                                  )
object DeploymentSpaceResource {
  implicit val encoder: Encoder[DeploymentSpaceResource] = deriveEncoder
  implicit val decoder: Decoder[DeploymentSpaceResource] = deriveDecoder
}

case class DeploymentSpaceListResponse(
                                        first: Option[Map[String, String]],
                                        limit: Int,
                                        resources: List[DeploymentSpaceResource]
                                      )
object DeploymentSpaceListResponse {
  implicit val encoder: Encoder[DeploymentSpaceListResponse] = deriveEncoder
  implicit val decoder: Decoder[DeploymentSpaceListResponse] = deriveDecoder
}

// --- Other shared models that need encoders/decoders ---
// TokenResponse, if defined in shared
// ModelDownloaderPod and DownloadedModel
// (Add these if they are in shared and used in API communication)

// If TokenResponse is in a shared file (e.g., org/ibm/shared/package.scala or separate file)
// case class TokenResponse(token: String)
// object TokenResponse {
//   implicit val encoder: Encoder[TokenResponse] = deriveEncoder
//   implicit val decoder: Decoder[TokenResponse] = deriveDecoder
// }

// If ModelDownloaderPod is in shared and used directly in API bodies
// case class ModelDownloaderPod(...)
// object ModelDownloaderPod {
//   implicit val encoder: Encoder[ModelDownloaderPod] = deriveEncoder
//   implicit val decoder: Decoder[ModelDownloaderPod] = deriveDecoder
// }

// If DownloadedModel is in shared and used directly in API bodies
// case class DownloadedModel(...)
// object DownloadedModel {
//   implicit val encoder: Encoder[DownloadedModel] = deriveEncoder
//   implicit val decoder: Decoder[DownloadedModel] = deriveDecoder
