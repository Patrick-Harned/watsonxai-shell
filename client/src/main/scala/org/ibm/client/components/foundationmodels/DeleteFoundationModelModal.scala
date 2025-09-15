package org.ibm.client.components.foundationmodels

import com.raquo.laminar.api.L.*
import org.ibm.client.components.notifications.NotificationManager
import org.ibm.client.components.{Component, cds}
import org.ibm.shared.{CustomFoundationModel, WatsonxAIIFM}
import org.ibm.tel.components.Modal
import sttp.capabilities.WebSockets
import sttp.client3.*
import sttp.client3.circe.*

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

class DeleteFoundationModelModal(onSuccess: () => Unit) extends Component {

  val backend: SttpBackend[Future, WebSockets] = FetchBackend()

  // Modal state - Updated for foundation models
  val isSubmittingVar = Var(false)
  val modelsToDeleteVar = Var[List[CustomFoundationModel]](List.empty)
  val errorVar = Var[Option[String]](None)

  def setModelsToDelete(models: List[CustomFoundationModel]): Unit = {
    modelsToDeleteVar.set(models)
    errorVar.set(None)
  }

  def deleteFoundationModels(): Unit = {
    val models = modelsToDeleteVar.now()
    if (models.isEmpty) return

    isSubmittingVar.set(true)
    errorVar.set(None)

    // Delete foundation models one by one from the CR
    val deleteFutures = models.map { model =>
      basicRequest
        .delete(uri"/api/watsonxaiifm/${model.model_id}")
        .response(asJson[WatsonxAIIFM])
        .send(backend)
        .map(_.body match {
          case Right(_) => Right(model.model_id)
          case Left(err) => Left(s"${model.model_id}: $err")
        })
    }

    Future.sequence(deleteFutures).foreach { results =>
      isSubmittingVar.set(false)

      val successes = results.collect { case Right(model_id) => model_id }
      val failures = results.collect { case Left(error) => error }

      if (successes.nonEmpty && failures.isEmpty) {
        // All successful
        modal.close()
        NotificationManager.showSuccess(
          if (successes.length == 1)
            s"Successfully deleted foundation model: ${successes.head}"
          else
            s"Successfully deleted ${successes.length} foundation models",
          "Foundation Models Deleted"
        )
        onSuccess()
      } else if (successes.nonEmpty && failures.nonEmpty) {
        // Partial success
        modal.close()
        NotificationManager.showError(
          s"Deleted ${successes.length} foundation models, but failed to delete: ${failures.mkString(", ")}",
          "Partial Success"
        )
        onSuccess() // Still refresh to show current state
      } else {
        // All failed
        errorVar.set(Some(s"Failed to delete foundation models: ${failures.mkString(", ")}"))
        NotificationManager.showError(
          s"Failed to delete foundation models: ${failures.mkString(", ")}",
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
        child <-- modelsToDeleteVar.signal.map { models =>
          if (models.length == 1) {
            val model = models.head
            div(
              p(s"Are you sure you want to delete the foundation model '${model.model_id}'?"),
              div(
                className := "model-details",
                p(s"Model ID: ${model.model_id}"),
                p(s"PVC: ${model.location.pvc_name}"),
                p(s"Path: ${model.location.sub_path}"),
                p(s"Tags: ${model.tags.mkString(", ")}")
              ),
              p(
                className := "delete-warning-text",
                "This action will remove the foundation model registration from WatsonX AI. " +
                  "The model files will remain on the PVC, but the model will no longer be available for inference."
              )
            )
          } else if (models.length > 1) {
            div(
              p(s"Are you sure you want to delete ${models.length} foundation models?"),
              div(
                className := "model-list",
                models.map { model =>
                  div(
                    className := "model-item",
                    s"• ${model.model_id} (${model.location.pvc_name}/${model.location.sub_path})"
                  )
                }
              ),
              p(
                className := "delete-warning-text",
                "This action will remove the foundation model registrations from WatsonX AI. " +
                  "The model files will remain on their respective PVCs, but the models will no longer be available for inference."
              )
            )
          } else {
            p("No foundation models selected for deletion.")
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
          modelsToDeleteVar.signal
        ).map { case (isSubmitting, models) =>
          isSubmitting || models.isEmpty
        },
        onClick --> { _ => deleteFoundationModels() },
        child.text <-- isSubmittingVar.signal.map(if (_) "Deleting..." else "Delete")
      )
    )
  )

  // Create the modal
  private lazy val modal = new Modal(
    title = "Delete Foundation Models",
    subtitle = "", // We'll handle the subtitle in the content
    content = modalContent,
    size = "md", // Smaller size for confirmation dialog
    id = Some("delete-foundation-model-modal")
  )

  // Public methods
  def open(models: List[CustomFoundationModel]): Unit = {
    setModelsToDelete(models)
    modal.open()
  }

  def close(): Unit = {
    modal.close()
  }

  // Expose the element
  val element: Element = modal.element
}

// Companion object for easy creation
object DeleteFoundationModelModal extends Component {
  def apply(onSuccess: () => Unit): DeleteFoundationModelModal = new DeleteFoundationModelModal(onSuccess)
}
