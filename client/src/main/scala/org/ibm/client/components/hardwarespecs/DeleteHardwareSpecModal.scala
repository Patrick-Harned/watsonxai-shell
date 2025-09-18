package org.ibm.client.components.hardwarespecs

import com.raquo.laminar.api.L.*
import org.ibm.client.components.notifications.NotificationManager
import org.ibm.client.components.{Component, cds}
import org.ibm.shared.{HardwareSpecificationResource} // Import your shared domain model
import org.ibm.tel.components.Modal // Assuming your Modal component
import sttp.capabilities.WebSockets
import sttp.client3.*
import sttp.client3.circe.*

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

class DeleteHardwareSpecModal(onSuccess: () => Unit) extends Component {

  val backend: SttpBackend[Future, WebSockets] = FetchBackend() // Client-side fetch backend

  // Modal state
  val isSubmittingVar = Var(false)
  val specsToDeleteVar = Var[List[HardwareSpecificationResource]](List.empty)
  val errorVar = Var[Option[String]](None)

  def setSpecsToDelete(specs: List[HardwareSpecificationResource]): Unit = {
    specsToDeleteVar.set(specs)
    errorVar.set(None)
  }

  def deleteHardwareSpecs(): Unit = {
    val specs = specsToDeleteVar.now()
    if (specs.isEmpty) return

    isSubmittingVar.set(true)
    errorVar.set(None)

    val deleteFutures = specs.map { spec =>
      // Need space_id for deletion from the metadata of the resource
      val spaceId = spec.metadata.space_id
      basicRequest
        .delete(uri"/api/watsonxai/hardware_specs/${spec.metadata.asset_id}?space_id=$spaceId") // Your server endpoint
        .send(backend)
        .map { resp =>
          if (resp.code.isSuccess) Right(spec.metadata.name)
          else Left(s"${spec.metadata.name}: Failed to delete (Status: ${resp.code}, Body: ${resp.body})")
        }
    }

    Future.sequence(deleteFutures).foreach { results =>
      isSubmittingVar.set(false)

      val successes = results.collect { case Right(name) => name }
      val failures = results.collect { case Left(error) => error }

      if (successes.nonEmpty && failures.isEmpty) {
        // All successful
        modal.close()
        NotificationManager.showSuccess(
          if (successes.length == 1)
            s"Successfully deleted hardware specification: ${successes.head}"
          else
            s"Successfully deleted ${successes.length} hardware specifications",
          "Specifications Deleted"
        )
        onSuccess()
      } else if (successes.nonEmpty && failures.nonEmpty) {
        // Partial success
        modal.close()
        NotificationManager.showError(
          s"Deleted ${successes.length} specs, but failed to delete: ${failures.mkString(", ")}",
          "Partial Success"
        )
        onSuccess() // Still refresh to show current state
      } else {
        // All failed
        errorVar.set(Some(s"Failed to delete hardware specifications: ${failures.mkString(", ")}"))
        NotificationManager.showError(
          s"Failed to delete hardware specifications: ${failures.mkString(", ")}",
          "Deletion Failed"
        )
      }
    }
  }

  // Create the modal content
  private lazy val modalContent = div(
    // Error notification
    child <-- errorVar.signal.map {
      case Some(error) =>
        cds"inline-notification"(
          strattr("kind") := "error",
          strattr("title") := "Deletion Error",
          strattr("subtitle") := error,
          marginBottom := "1rem"
        )
      case None => emptyNode
    },

    // Confirmation message
    div(
      className := "delete-confirmation-content",

      // Warning icon and message
      div(
        className := "delete-warning",
        span(className := "delete-warning-icon", "⚠️"),
        child <-- specsToDeleteVar.signal.map { specs =>
          if (specs.length == 1) {
            val spec = specs.head
            div(
              p(s"Are you sure you want to delete the hardware specification '${spec.metadata.name}' (ID: ${spec.metadata.asset_id})?"),
              div(
                className := "spec-details",
                p(s"Description: ${spec.metadata.description.getOrElse("N/A")}"),
                p(s"Space ID: ${spec.metadata.space_id}"),
                p(s"CPU: ${spec.entity.hardware_specification.nodes.flatMap(x => x.cpu.map(_.units)).getOrElse("N/A")}, Memory: ${spec.entity.hardware_specification.nodes.flatMap(x =>x.mem.map(_.size)).getOrElse("N/A")}")
              ),
              p(
                className := "delete-warning-text",
                "This action will permanently remove the hardware specification from WatsonX AI."
              )
            )
          } else if (specs.length > 1) {
            div(
              p(s"Are you sure you want to delete ${specs.length} hardware specifications?"),
              div(
                className := "spec-list",
                specs.map { spec =>
                  div(
                    className := "spec-item",
                    s"• ${spec.metadata.name} (ID: ${spec.metadata.asset_id}, Space: ${spec.metadata.space_id})"
                  )
                }
              ),
              p(
                className := "delete-warning-text",
                "This action will permanently remove these hardware specifications from WatsonX AI."
              )
            )
          } else {
            p("No hardware specifications selected for deletion.")
          }
        }
      )
    ),

    // Modal footer
    cds"modal-footer"(
      cds"modal-footer-button"(
        strattr("kind") := "secondary",
        onClick --> { _ => close() },
        "Cancel"
      ),
      cds"modal-footer-button"(
        strattr("kind") := "danger",
        disabled <-- Signal.combine(
          isSubmittingVar.signal,
          specsToDeleteVar.signal
        ).map { case (isSubmitting, specs) =>
          isSubmitting || specs.isEmpty
        },
        onClick --> { _ => deleteHardwareSpecs() },
        child.text <-- isSubmittingVar.signal.map(if (_) "Deleting..." else "Delete")
      )
    )
  )

  // Create the modal
  private lazy val modal = new Modal(
    title = "Delete Hardware Specifications",
    subtitle = "", // Subtitle handled in content for flexibility
    content = modalContent,
    size = "md",
    id = Some("delete-hardware-spec-modal")
  )

  // Public methods
  def open(specs: List[HardwareSpecificationResource]): Unit = {
    setSpecsToDelete(specs)
    modal.open()
  }

  def close(): Unit = {
    modal.close()
  }

  // Expose the element
  val element: Element = modal.element
}

// Companion object for easy creation
object DeleteHardwareSpecModal extends Component {
  def apply(onSuccess: () => Unit): DeleteHardwareSpecModal = new DeleteHardwareSpecModal(onSuccess)
}
