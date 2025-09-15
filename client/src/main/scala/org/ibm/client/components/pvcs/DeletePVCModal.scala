package org.ibm.client.components

import com.raquo.laminar.api.L.*
import org.ibm.client.components.notifications.NotificationManager
import org.ibm.client.components.pvcs.PVCDataTable.PVCDataRow
import org.ibm.tel.components.Modal
import org.ibm.shared.PVC
import sttp.capabilities.WebSockets
import sttp.client3.*
import sttp.client3.circe.*

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

class DeletePVCModal(onSuccess: () => Unit) extends Component {

  val backend: SttpBackend[Future, WebSockets] = FetchBackend()

  // Modal state
  val isSubmittingVar = Var(false)
  val pvcsToDeleteVar = Var[List[PVCDataRow]](List.empty)
  val errorVar = Var[Option[String]](None)

  def setPVCsToDelete(pvcs: List[PVCDataRow]): Unit = {
    pvcsToDeleteVar.set(pvcs)
    errorVar.set(None)
  }

  def deletePVCs(): Unit = {
    val pvcs = pvcsToDeleteVar.now()
    if (pvcs.isEmpty) return

    isSubmittingVar.set(true)
    errorVar.set(None)

    // Delete PVCs one by one (could be optimized for batch operations)
    val deleteFutures = pvcs.map { pvc =>
      basicRequest
        .delete(uri"/api/watsonxai/pvcs/${pvc.name}")
        .response(asString)
        .send(backend)
        .map(_.body match {
          case Right(_) => Right(pvc.name)
          case Left(err) => Left(s"${pvc.name}: $err")
        })
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
            s"Successfully deleted PVC: ${successes.head}"
          else
            s"Successfully deleted ${successes.length} PVCs",
          "PVCs Deleted"
        )
        onSuccess()
      } else if (successes.nonEmpty && failures.nonEmpty) {
        // Partial success
        modal.close()
        NotificationManager.showError(
          s"Deleted ${successes.length} PVCs, but failed to delete: ${failures.mkString(", ")}",
          "Partial Success"
        )
        onSuccess() // Still refresh to show current state
      } else {
        // All failed
        errorVar.set(Some(s"Failed to delete PVCs: ${failures.mkString(", ")}"))
        NotificationManager.showError(
          s"Failed to delete PVCs: ${failures.mkString(", ")}",
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
        child <-- pvcsToDeleteVar.signal.map { pvcs =>
          if (pvcs.length == 1) {
            div(
              p(s"Are you sure you want to delete the PVC '${pvcs.head.name}'?"),
              p(
                className := "delete-warning-text",
                "This action cannot be undone. All data stored in this PVC will be permanently lost."
              )
            )
          } else if (pvcs.length > 1) {
            div(
              p(s"Are you sure you want to delete ${pvcs.length} PVCs?"),
              div(
                className := "pvc-list",
                pvcs.map { pvc =>
                  div(
                    className := "pvc-item",
                    s"• ${pvc.name}"
                  )
                }
              ),
              p(
                className := "delete-warning-text",
                "This action cannot be undone. All data stored in these PVCs will be permanently lost."
              )
            )
          } else {
            p("No PVCs selected for deletion.")
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
          pvcsToDeleteVar.signal
        ).map { case (isSubmitting, pvcs) =>
          isSubmitting || pvcs.isEmpty
        },
        onClick --> { _ => deletePVCs() },
        child.text <-- isSubmittingVar.signal.map(if (_) "Deleting..." else "Delete")
      )
    )
  )

  // Create the modal - following the same pattern as CreatePVCModal
  private lazy val modal = new Modal(
    title = "Delete Persistent Volume Claims",
    subtitle = "", // We'll handle the subtitle in the content
    content = modalContent,
    size = "md", // Smaller size for confirmation dialog
    id = Some("delete-pvc-modal")
  )

  // Public methods
  def open(pvcs: List[PVCDataRow]): Unit = {
    setPVCsToDelete(pvcs)
    modal.open()
  }

  def close(): Unit = {
    modal.close()
  }

  // Expose the element
  val element: Element = modal.element
}

// Companion object for easy creation
object DeletePVCModal extends Component {
  def apply(onSuccess: () => Unit): DeletePVCModal = new DeletePVCModal(onSuccess)
}
