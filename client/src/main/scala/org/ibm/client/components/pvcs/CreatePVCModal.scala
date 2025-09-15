package org.ibm.client.components

import com.raquo.laminar.api.L.*
import com.raquo.laminar.api.L.svg
import org.ibm.client.components.notifications.NotificationManager
import org.ibm.tel.components.Modal
import org.ibm.shared.{CreatePVCRequest, StorageClass}
import sttp.capabilities.WebSockets
import sttp.client3.*
import sttp.client3.circe.*

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

class CreatePVCModal(onSuccess: () => Unit) extends Component {

  val backend: SttpBackend[Future, WebSockets] = FetchBackend()

  // Form state
  val nameVar = Var("")
  val capacityVar = Var("")
  val storageClassVar = Var("")
  val isSubmittingVar = Var(false)
  val storageClassesVar = Var[List[StorageClass]](List.empty)
  val errorVar = Var[Option[String]](None)
  val isLoadingStorageClassesVar = Var(true)

  def fetchStorageClasses(): Unit = {
    isLoadingStorageClassesVar.set(true)
    basicRequest
      .get(uri"/api/watsonxai/storageclasses")
      .response(asJson[List[StorageClass]])
      .send(backend)
      .map(_.body)
      .foreach {
        case Right(storageClasses) =>
          storageClassesVar.set(storageClasses)
          isLoadingStorageClassesVar.set(false)
          if (storageClasses.nonEmpty && storageClassVar.now().isEmpty) {
            storageClassVar.set(storageClasses.head.metadata.name)
          }
        case Left(err) =>
          errorVar.set(Some(s"Failed to load storage classes: $err"))
          isLoadingStorageClassesVar.set(false)
      }
  }

  def validateForm(): Option[String] = {
    val name = nameVar.now().trim
    val capacity = capacityVar.now().trim
    val storageClass = storageClassVar.now().trim

    if (name.isEmpty) {
      Some("PVC name is required")
    } else if (!name.matches("^[a-z0-9]([-a-z0-9]*[a-z0-9])?$")) {
      Some("PVC name must be lowercase alphanumeric with hyphens")
    } else if (capacity.isEmpty) {
      Some("Capacity is required")
    } else if (!capacity.matches("^\\d+$")) {
      Some("Capacity must be a number")
    } else if (storageClass.isEmpty) {
      Some("Storage class is required")
    } else {
      None
    }
  }

  def resetForm(): Unit = {
    nameVar.set("")
    capacityVar.set("")
    storageClassVar.set("")
    errorVar.set(None)
  }

  def submitForm(): Unit = {
    validateForm() match {
      case Some(error) =>
        errorVar.set(Some(error))
      case None =>
        isSubmittingVar.set(true)
        errorVar.set(None)

        val request = CreatePVCRequest(
          name = nameVar.now().trim,
          storageClassName = storageClassVar.now().trim,
          size = s"${capacityVar.now().trim}Gi",
          accessModes = List("ReadWriteOnce"),
          labels = Some(Map(
            "created-by" -> "watsonx-ui",
            "purpose" -> "user-storage"
          ))
        )

        basicRequest
          .post(uri"/api/watsonxai/pvcs")
          .body(request)
          .response(asJson[org.ibm.shared.PVC])
          .send(backend)
          .map(_.body)
          .foreach {
            case Right(pvc) =>
              isSubmittingVar.set(false)
              modal.close()
              resetForm()
              NotificationManager.showSuccess(
                s"Successfully created PVC: ${pvc.metadata.name}",
                "PVC Created"
              )
              onSuccess()
            case Left(err) =>
              isSubmittingVar.set(false)
              errorVar.set(Some(s"Failed to create PVC: $err"))
              NotificationManager.showError(
                s"Failed to create PVC: $err",
                "Creation Failed"
              )
          }
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

    // PVC Name input
    div(
      className := "cds--form-item",
      label(
        className := "cds--label",
        forId := "pvc-name",
        "PVC Name"
      ),
      input(
        className := "cds--text-input",
        typ := "text",
        idAttr := "pvc-name",
        placeholder := "e.g. my-model-storage",
        controlled(
          value <-- nameVar,
          onInput.mapToValue --> nameVar
        )
      ),
      div(
        className := "cds--form__helper-text",
        "Must be lowercase alphanumeric with hyphens"
      )
    ),

    // Capacity input
    div(
      className := "cds--form-item",
      label(
        className := "cds--label",
        forId := "pvc-capacity",
        "Capacity (GB)"
      ),
      input(
        className := "cds--text-input",
        typ := "number",
        idAttr := "pvc-capacity",
        placeholder := "100",
        minAttr := "1",
        controlled(
          value <-- capacityVar,
          onInput.mapToValue --> capacityVar
        )
      ),
      div(
        className := "cds--form__helper-text",
        "Storage capacity in gigabytes"
      )
    ),

    // Storage Class dropdown
    div(
      className := "cds--form-item",
      label(
        className := "cds--label",
        forId := "storage-class",
        "Storage Class"
      ),
      div(
        className := "cds--select",
        child <-- Signal.combine(
          storageClassesVar.signal,
          isLoadingStorageClassesVar.signal
        ).map { case (storageClasses, isLoading) =>
          if (isLoading) {
            select(
              className := "cds--select-input",
              idAttr := "storage-class",
              disabled := true,
              option(value := "", "Loading storage classes...")
            )
          } else {
            select(
              className := "cds--select-input",
              idAttr := "storage-class",
              controlled(
                value <-- storageClassVar,
                onChange.mapToValue --> storageClassVar
              ),
              option(
                value := "",
                disabled := true,
                "Select storage class"
              ),
              storageClasses.map { sc =>
                option(
                  value := sc.metadata.name,
                  sc.metadata.name
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
        "Storage class for the persistent volume"
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
          nameVar.signal,
          capacityVar.signal,
          storageClassVar.signal
        ).map { case (isSubmitting, name, capacity, storageClass) =>
          isSubmitting || name.trim.isEmpty || capacity.trim.isEmpty || storageClass.trim.isEmpty
        },
        onClick --> { _ => submitForm() },
        child.text <-- isSubmittingVar.signal.map(if (_) "Creating..." else "Create PVC")
      )
    )
  )

  // Create the modal with the form as content - EXACTLY like your working example
  private lazy val modal = new Modal(
    title = "Create Persistent Volume Claim",
    subtitle = "Create a new Persistent Volume Claim for storing data. The PVC will be created with the watsonxai/pvc=true label.",
    content = formContent,
    size = "lg",
    id = Some("create-pvc-modal")
  )

  // Public methods - EXACTLY like your working example
  def open(): Unit = {
    fetchStorageClasses() // Fetch storage classes when opening
    modal.open()
  }

  def close(): Unit = {
    modal.close()
    resetForm()
  }

  // Expose the element - EXACTLY like your working example
  val element: Element = modal.element
}

// Companion object for easy creation
object CreatePVCModal extends Component {
  def apply(onSuccess: () => Unit): CreatePVCModal = new CreatePVCModal(onSuccess)

  // For backward compatibility
  def render(isOpenVar: Var[Boolean], onSuccess: () => Unit): Element = {
    val modal = new CreatePVCModal(onSuccess)

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
