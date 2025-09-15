package org.ibm.client.components.modeldownloads

import com.raquo.laminar.api.L.*
import org.ibm.client.components.{Component, cds}
import org.ibm.client.components.notifications.NotificationManager
import org.ibm.shared.{CreateModelDownloaderRequest, ModelDownloaderPod, PVC}
import org.ibm.tel.components.Modal
import sttp.capabilities.WebSockets
import sttp.client3.*
import sttp.client3.circe.*

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

class CreateModelDownloaderModal(onSuccess: () => Unit) extends Component {

  val backend: SttpBackend[Future, WebSockets] = FetchBackend()

  // Form state
  val modelRepoVar = Var("")
  val localDirNameVar = Var("")
  val pvcVar = Var("")
  val namespaceVar = Var("default")
  val isSubmittingVar = Var(false)
  val pvcs = Var[List[PVC]](List.empty)
  val errorVar = Var[Option[String]](None)
  val isLoadingPvcs = Var(true)

  def fetchPvcs(): Unit = {
    isLoadingPvcs.set(true)
    basicRequest
      .get(uri"/api/watsonxai/pvcs")
      .response(asJson[List[PVC]])
      .send(backend)
      .map(_.body)
      .foreach {
        case Right(pvcsResponse) =>
          pvcs.set(pvcsResponse)
          isLoadingPvcs.set(false)
          if (pvcsResponse.nonEmpty && pvcVar.now().isEmpty) {
            pvcVar.set(pvcsResponse.head.metadata.name)
          }
        case Left(err) =>
          errorVar.set(Some(s"Failed to load PVCs: $err"))
          isLoadingPvcs.set(false)
      }
  }

  def validateForm(): Option[String] = {
    val modelRepo = modelRepoVar.now().trim
    val localDirName = localDirNameVar.now().trim
    val pvc = pvcVar.now().trim
    val namespace = namespaceVar.now().trim

    if (modelRepo.isEmpty) {
      Some("Model repository is required")
    } else if (!modelRepo.contains("/")) {
      Some("Model repository must be in format 'username/model-name'")
    } else if (localDirName.isEmpty) {
      Some("Local directory name is required")
    } else if (!localDirName.matches("^[a-zA-Z0-9][a-zA-Z0-9._-]*$")) {
      Some("Local directory name must be alphanumeric with dots, hyphens, or underscores")
    } else if (pvc.isEmpty) {
      Some("PVC selection is required")
    } else if (namespace.isEmpty) {
      Some("Namespace is required")
    } else if (!namespace.matches("^[a-z0-9]([-a-z0-9]*[a-z0-9])?$")) {
      Some("Namespace must be lowercase alphanumeric with hyphens")
    } else {
      None
    }
  }

  def resetForm(): Unit = {
    modelRepoVar.set("")
    localDirNameVar.set("")
    pvcVar.set("")
    namespaceVar.set("default")
    errorVar.set(None)
  }

  def submitForm(): Unit = {
    validateForm() match {
      case Some(error) =>
        errorVar.set(Some(error))
      case None =>
        isSubmittingVar.set(true)
        errorVar.set(None)

        val request = CreateModelDownloaderRequest(
          pvcName = pvcVar.now().trim,
          modelRepo = modelRepoVar.now().trim,
          localDirName = localDirNameVar.now().trim,
          namespace = namespaceVar.now().trim
        )

        basicRequest
          .post(uri"/api/watsonxai/modeldownloaders")
          .body(request)
          .response(asJson[ModelDownloaderPod])
          .send(backend)
          .map(_.body)
          .foreach {
            case Right(pod) =>
              isSubmittingVar.set(false)
              modal.close()
              resetForm()
              NotificationManager.showSuccess(
                s"Successfully created model downloader: ${pod.name}",
                "Model Download Started"
              )
              onSuccess()
            case Left(err) =>
              isSubmittingVar.set(false)
              errorVar.set(Some(s"Failed to create model downloader: $err"))
              NotificationManager.showError(
                s"Failed to create model downloader: $err",
                "Creation Failed"
              )
          }
    }
  }

  // Auto-generate directory name from model repo
  def autoGenerateLocalDir(): Unit = {
    val modelRepo = modelRepoVar.now().trim
    if (modelRepo.contains("/") && localDirNameVar.now().trim.isEmpty) {
      val dirName = modelRepo.split("/").last
      localDirNameVar.set(dirName)
    }
  }

  // Create the form content
  private lazy val formContent = div(
    // Error notification
    child <-- errorVar.signal.map {
      case Some(error) =>
        cds"inline-notification"(
          strattr("kind") := "error",
          strattr("title") := "Validation Error",
          strattr("subtitle") := error,
          marginBottom := "1rem"
        )
      case None => emptyNode
    },

    // Model Repository input
    div(
      className := "cds--form-item",
      label(
        className := "cds--label",
        forId := "model-repo",
        "Model Repository"
      ),
      input(
        className := "cds--text-input",
        typ := "text",
        idAttr := "model-repo",
        placeholder := "e.g. TheBloke/CapybaraHermes-2.5-Mistral-7B-GPTQ",
        controlled(
          value <-- modelRepoVar,
          onInput.mapToValue --> modelRepoVar.writer.contramap[String] { value =>
            autoGenerateLocalDir()
            value
          }
        )
      ),
      div(
        className := "cds--form__helper-text",
        "HuggingFace model repository in format 'username/model-name'"
      )
    ),

    // Local Directory Name input
    div(
      className := "cds--form-item",
      label(
        className := "cds--label",
        forId := "local-dir-name",
        "Local Directory Name"
      ),
      input(
        className := "cds--text-input",
        typ := "text",
        idAttr := "local-dir-name",
        placeholder := "e.g. CapybaraHermes-2.5-Mistral-7B-GPTQ",
        controlled(
          value <-- localDirNameVar,
          onInput.mapToValue --> localDirNameVar
        )
      ),
      div(
        className := "cds--form__helper-text",
        "Directory name where the model will be stored (auto-generated from repository name)"
      )
    ),

    // PVC Selection dropdown
    div(
      className := "cds--form-item",
      label(
        className := "cds--label",
        forId := "pvc-selection",
        "Storage PVC"
      ),
      div(
        className := "cds--select",
        child <-- Signal.combine(
          pvcs.signal,
          isLoadingPvcs.signal
        ).map { case (pvcList, isLoading) =>
          if (isLoading) {
            select(
              className := "cds--select-input",
              idAttr := "pvc-selection",
              disabled := true,
              option(value := "", "Loading PVCs...")
            )
          } else if (pvcList.isEmpty) {
            select(
              className := "cds--select-input",
              idAttr := "pvc-selection",
              disabled := true,
              option(value := "", "No PVCs available")
            )
          } else {
            select(
              className := "cds--select-input",
              idAttr := "pvc-selection",
              controlled(
                value <-- pvcVar,
                onChange.mapToValue --> pvcVar
              ),
              option(
                value := "",
                disabled := true,
                "Select PVC for storage"
              ),
              pvcList.map { pvc =>
                option(
                  value := pvc.metadata.name,
                  s"${pvc.metadata.name} (${pvc.spec.resources.requests.storage  })"
                )
              }
            )
          }
        },
        svg.svg(
          svg.className := "cds--select__arrow",
          svg.width := "16",
          svg.height := "16",
          svg.viewBox := "0 0 16 16",
          svg.fill := "currentColor",
          svg.path(svg.d := "m8 11L3 6l0.7-0.7L8 9.6l4.3-4.3L13 6z")
        )
      ),
      div(
        className := "cds--form__helper-text",
        "PVC where the model will be downloaded and stored"
      )
    ),

    // Namespace input
    div(
      className := "cds--form-item",
      label(
        className := "cds--label",
        forId := "namespace",
        "Namespace"
      ),
      input(
        className := "cds--text-input",
        typ := "text",
        idAttr := "namespace",
        placeholder := "default",
        controlled(
          value <-- namespaceVar,
          onInput.mapToValue --> namespaceVar
        )
      ),
      div(
        className := "cds--form__helper-text",
        "Kubernetes namespace where the downloader pod will run"
      )
    ),

    // Modal footer
    cds"modal-footer"(
      cds"modal-footer-button"(
        strattr("kind") := "secondary",
        onClick --> { _ =>
          close()
          resetForm()
        },
        "Cancel"
      ),
      cds"modal-footer-button"(
        disabled <-- Signal.combine(
          isSubmittingVar.signal,
          modelRepoVar.signal,
          localDirNameVar.signal,
          pvcVar.signal,
          namespaceVar.signal
        ).map { case (isSubmitting, modelRepo, localDir, pvc, namespace) =>
          isSubmitting || modelRepo.trim.isEmpty || localDir.trim.isEmpty || pvc.trim.isEmpty || namespace.trim.isEmpty
        },
        onClick --> { _ => submitForm() },
        child.text <-- isSubmittingVar.signal.map(if (_) "Creating..." else "Start Download")
      )
    )
  )

  // Create the modal with the form as content
  private lazy val modal = new Modal(
    title = "Create Model Downloader",
    subtitle = "Download a model from HuggingFace Hub to a PVC. The downloader will run as a Kubernetes pod and stream the model files to your selected storage.",
    content = formContent,
    size = "lg",
    id = Some("create-model-downloader-modal")
  )

  // Public methods
  def open(): Unit = {
    fetchPvcs() // Fetch PVCs when opening
    modal.open()
  }

  def close(): Unit = {
    modal.close()
    resetForm()
  }

  // Expose the element
  val element: Element = modal.element
}

// Companion object for easy creation
object CreateModelDownloaderModal extends Component {
  def apply(onSuccess: () => Unit): CreateModelDownloaderModal = new CreateModelDownloaderModal(onSuccess)

  // For backward compatibility
  def render(isOpenVar: Var[Boolean], onSuccess: () => Unit): Element = {
    val modal = new CreateModelDownloaderModal(onSuccess)

    div(
      modal.element,
      onMountCallback { ctx =>
        isOpenVar.signal.foreach { isOpen =>
          if (isOpen) modal.open() else modal.close()
        }(ctx.owner)
      }
    )
  }
}
