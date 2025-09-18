// org/ibm/shared/HardwareSpec.scala (Updated with Circe Implicits)
package org.ibm.shared

import java.time.Instant
import io.circe.{Decoder, Encoder}
import io.circe.generic.semiauto.* // <-- Use semiauto for explicit derivation

// --- Models for creating a HardwareSpec ---
case class CpuSpec(units: String)
object CpuSpec {
  implicit val encoder: Encoder[CpuSpec] = deriveEncoder
  implicit val decoder: Decoder[CpuSpec] = deriveDecoder
}

case class MemSpec(size: String)
object MemSpec {
  implicit val encoder: Encoder[MemSpec] = deriveEncoder
  implicit val decoder: Decoder[MemSpec] = deriveDecoder
}

case class GpuSpec(num_gpu: Int)
object GpuSpec {
  implicit val encoder: Encoder[GpuSpec] = deriveEncoder
  implicit val decoder: Decoder[GpuSpec] = deriveDecoder
}

case class NodeSpecs(
                      cpu: Option[CpuSpec] = None,
                      mem: Option[MemSpec] = None,
                      gpu: Option[GpuSpec] = None
                    )
object NodeSpecs {
  implicit val encoder: Encoder[NodeSpecs] = deriveEncoder
  implicit val decoder: Decoder[NodeSpecs] = deriveDecoder
}

case class HardwareSpecificationCreate(
                                        name: String,
                                        description: Option[String] = None,
                                        nodes: NodeSpecs
                                      )
object HardwareSpecificationCreate {
  implicit val encoder: Encoder[HardwareSpecificationCreate] = deriveEncoder
  implicit val decoder: Decoder[HardwareSpecificationCreate] = deriveDecoder
}

// --- Models for the full GET response ---

case class NodeSelector(label_name: String, label_value: String)
object NodeSelector {
  implicit val encoder: Encoder[NodeSelector] = deriveEncoder
  implicit val decoder: Decoder[NodeSelector] = deriveDecoder
}

case class DriverCpuSpec(units: String, model: Option[String] = None)
object DriverCpuSpec {
  implicit val encoder: Encoder[DriverCpuSpec] = deriveEncoder
  implicit val decoder: Decoder[DriverCpuSpec] = deriveDecoder
}

case class DriverMemSpec(size: String)
object DriverMemSpec {
  implicit val encoder: Encoder[DriverMemSpec] = deriveEncoder
  implicit val decoder: Decoder[DriverMemSpec] = deriveDecoder
}

case class DriverSpecs(
                        cpu: Option[DriverCpuSpec] = None,
                        mem: Option[DriverMemSpec] = None
                      )
object DriverSpecs {
  implicit val encoder: Encoder[DriverSpecs] = deriveEncoder
  implicit val decoder: Decoder[DriverSpecs] = deriveDecoder
}

case class WorkerPool(id: Long, provider: String)
object WorkerPool {
  implicit val encoder: Encoder[WorkerPool] = deriveEncoder
  implicit val decoder: Decoder[WorkerPool] = deriveDecoder
}

case class HardwareNodeDetails(
                                cpu: Option[DriverCpuSpec] = None,
                                mem: Option[MemSpec] = None,
                                gpu: Option[GpuSpecDetails] = None,
                                node_selector: Option[List[NodeSelector]] = None,
                                num_nodes: Option[Int] = None,
                                num_drivers: Option[Int] = None,
                                drivers: Option[DriverSpecs] = None,
                                worker_pool: Option[WorkerPool] = None
                              )
object HardwareNodeDetails {
  implicit val encoder: Encoder[HardwareNodeDetails] = deriveEncoder
  implicit val decoder: Decoder[HardwareNodeDetails] = deriveDecoder
}

case class GpuSpecDetails(
                           num_gpu: Int,
                           name: Option[String] = None,
                           gpu_profile: Option[String] = None,
                           mig_profile: Option[String] = None
                         )
object GpuSpecDetails {
  implicit val encoder: Encoder[GpuSpecDetails] = deriveEncoder
  implicit val decoder: Decoder[GpuSpecDetails] = deriveDecoder
}

case class HardwareSpecificationEntity(
                                        hardware_specification: HardwareEntityDetails
                                      )
object HardwareSpecificationEntity {
  implicit val encoder: Encoder[HardwareSpecificationEntity] = deriveEncoder
  implicit val valdecoder: Decoder[HardwareSpecificationEntity] = deriveDecoder
}

case class HardwareEntityDetails(
                                  nodes: Option[HardwareNodeDetails]
                                )
object HardwareEntityDetails {
  implicit val encoder: Encoder[HardwareEntityDetails] = deriveEncoder
  implicit val decoder: Decoder[HardwareEntityDetails] = deriveDecoder
}

case class HardwareSpecificationMetadata(
                                          name: String,
                                          description: Option[String] = None,
                                          asset_type: String,
                                          asset_id: String,
                                          project_id: Option[String] = None,
                                          space_id: Option[String],
                                          owner_id:Option[ String],
                                          created_at: Instant,
                                          updated_at: Option[Instant],
                                          href: String
                                        )
object HardwareSpecificationMetadata {
  implicit val encoder: Encoder[HardwareSpecificationMetadata] = deriveEncoder
  implicit val decoder: Decoder[HardwareSpecificationMetadata] = deriveDecoder
}

case class HardwareSpecificationResource(
                                          metadata: HardwareSpecificationMetadata,
                                          entity: HardwareSpecificationEntity
                                        )
object HardwareSpecificationResource {
  implicit val encoder: Encoder[HardwareSpecificationResource] = deriveEncoder
  implicit val decoder: Decoder[HardwareSpecificationResource] = deriveDecoder
}

case class HardwareSpecificationList(
                                      total_results: Int,
                                      resources: List[HardwareSpecificationResource]
                                    )
object HardwareSpecificationList {
  implicit val encoder: Encoder[HardwareSpecificationList] = deriveEncoder
  implicit val decoder: Decoder[HardwareSpecificationList] = deriveDecoder
}


