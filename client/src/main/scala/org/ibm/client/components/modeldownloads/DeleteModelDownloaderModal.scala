package org.ibm.client.components.modeldownloads

import com.raquo.laminar.api.L.*
import org.ibm.client.components.{Component, cds}
import org.ibm.client.components.notifications.NotificationManager
import org.ibm.client.components.modeldownloads.ModelDownloaderDataTable.ModelDownloaderDataRow
import org.ibm.tel.components.Modal
import sttp.capabilities.WebSockets
import sttp.client3.*

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

class DeleteModelDownloaderModal(onSuccess: () => Unit) extends Component {

  val backend: SttpBackend[Future, WebSockets] = FetchBackend()

  // Modal state
  val isSubmittingVar = Var(false)
  val podsToDeleteVar = Var[List[ModelDownloaderDataRow]](List.empty)
  val errorVar = Var[Option[String]](None)

  def setPodsToDelete(pods: List[ModelDownloaderDataRow]): Unit = {
    podsToDeleteVar.set(pods)
    errorVar.set(None)
  }

  def deleteModelDownloaderPods(): Unit = {
    val pods = podsToDeleteVar.now()
    if (pods.isEmpty) return

    isSubmittingVar.set(true)
    errorVar.set(None)

    // Delete model downloader pods one by one
    val deleteFutures = pods.map { pod =>
      basicRequest
        .delete(uri"/api/watsonxai/modeldownloaders/${pod.name}?namespace=${pod.namespace}")
        .response(asString)
        .send(backend)
        .map(_.body match {
          case Right(_) => Right(pod.name)
          case Left(err) => Left(s"${pod.name}: $err")
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
            s"Successfully deleted model downloader: ${successes.head}"
          else
            s"Successfully deleted ${successes.length} model downloaders",
          "Model Downloaders Deleted"
        )
        onSuccess()
      } else if (successes.nonEmpty && failures.nonEmpty) {
        // Partial success
        modal.close()
        NotificationManager.showError(
          s"Deleted ${successes.length} model downloaders, but failed to delete: ${failures.mkString(", ")}",
          "Partial Success"
        )
        onSuccess() // Still refresh to show current state
      } else {
        // All failed
        errorVar.set(Some(s"Failed to delete model downloaders: ${failures.mkString(", ")}"))
        NotificationManager.showError(
          s"Failed to delete model downloaders: ${failures.mkString(", ")}",
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
        child <-- podsToDeleteVar.signal.map { pods =>
          if (pods.length == 1) {
            div(
              p(s"Are you sure you want to delete the model downloader '${pods.head.name}'?"),
              div(
                className := "pod-details",
                p(s"Model: ${pods.head.modelRepo}"),
                p(s"PVC: ${pods.head.pvc}"),
                p(s"Status: ${pods.head.status}"),
                p(s"Namespace: ${pods.head.namespace}")
              ),
              p(
                className := "delete-warning-text",
                "This action will terminate the download pod. If the download is in progress, it will be interrupted and may need to be restarted."
              )
            )
          } else if (pods.length > 1) {
            div(
              p(s"Are you sure you want to delete ${pods.length} model downloaders?"),
              div(
                className := "pod-list",
                pods.map { pod =>
                  div(
                    className := "pod-item",
                    s"• ${pod.name} (${pod.modelRepo}) - Status: ${pod.status}"
                  )
                }
              ),
              p(
                className := "delete-warning-text",
                "This action will terminate the download pods. Any downloads in progress will be interrupted and may need to be restarted."
              )
            )
          } else {
            p("No model downloaders selected for deletion.")
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
          podsToDeleteVar.signal
        ).map { case (isSubmitting, pods) =>
          isSubmitting || pods.isEmpty
        },
        onClick --> { _ => deleteModelDownloaderPods() },
        child.text <-- isSubmittingVar.signal.map(if (_) "Deleting..." else "Delete")
      )
    )
  )

  // Create the modal - following the same pattern as CreateModelDownloaderModal
  private lazy val modal = new Modal(
    title = "Delete Model Downloaders",
    subtitle = "", // We'll handle the subtitle in the content
    content = modalContent,
    size = "md", // Smaller size for confirmation dialog
    id = Some("delete-model-downloader-modal")
  )

  // Public methods
  def open(pods: List[ModelDownloaderDataRow]): Unit = {
    setPodsToDelete(pods)
    modal.open()
  }

  def close(): Unit = {
    modal.close()
  }

  // Expose the element
  val element: Element = modal.element
}

// Companion object for easy creation
object DeleteModelDownloaderModal extends Component {
  def apply(onSuccess: () => Unit): DeleteModelDownloaderModal = new DeleteModelDownloaderModal(onSuccess)
}
