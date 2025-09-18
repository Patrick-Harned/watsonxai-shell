package org.ibm.client.components.hardwarespecs

import com.raquo.laminar.api.L.*
import org.ibm.client.components.notifications.NotificationManager
import org.ibm.client.components.{Component, cds}
import org.ibm.shared.* // Import all shared models
import org.ibm.tel.components.Modal // Assuming your Modal component
import sttp.capabilities.WebSockets
import sttp.client3.*
import sttp.client3.circe.*
import org.scalajs.dom
import scala.scalajs.js

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

class CreateHardwareSpecModal(onSuccess: () => Unit) extends Component {

  val backend: SttpBackend[Future, WebSockets] = FetchBackend()

  // Form fields
  val nameVar = Var("")
  val descriptionVar = Var("")
  val selectedSpaceIdVar = Var[Option[String]](None) // New: For deployment space dropdown
  val cpuUnitsVar = Var("1") // Default to 1 CPU unit
  val memSizeVar = Var("4Gi") // Default to 4Gi RAM
  val gpuNumVar = Var(0) // Default to 0 GPUs

  // State
  val isSubmittingVar = Var(false)
  val errorVar = Var[Option[String]](None)
  val deploymentSpaces = Var[List[DeploymentSpaceResource]](List.empty) // New: List of deployment spaces
  val isLoadingDeploymentSpaces = Var(true)

  // Fetch deployment spaces
  def fetchDeploymentSpaces(): Unit = {
    isLoadingDeploymentSpaces.set(true)
    basicRequest
      .get(uri"/api/watsonxai/deployment_spaces") // Your server endpoint
      .response(asJson[List[DeploymentSpaceResource]])
      .send(backend)
      .map(_.body)
      .foreach {
        case Right(spaces) =>
          deploymentSpaces.set(spaces.filter(_.entity.status.map(_.state).contains("active"))) // Filter for active spaces
          isLoadingDeploymentSpaces.set(false)
          // Pre-select the first active space if available and none is selected
          if (selectedSpaceIdVar.now().isEmpty && deploymentSpaces.now().nonEmpty) {
            selectedSpaceIdVar.set(Some(deploymentSpaces.now().head.metadata.id))
          }
        case Left(err) =>
          errorVar.set(Some(s"Failed to load deployment spaces: $err"))
          isLoadingDeploymentSpaces.set(false)
      }
  }

  def validateForm(): Option[String] = {
    val name = nameVar.now().trim
    val cpuUnits = cpuUnitsVar.now().trim
    val memSize = memSizeVar.now().trim
    val gpuNum = gpuNumVar.now()
    val spaceId = selectedSpaceIdVar.now()

    if (name.isEmpty) {
      Some("Specification Name is required")
    } else if (spaceId.isEmpty) {
      Some("Deployment Space selection is required")
    } else if (cpuUnits.isEmpty || !cpuUnits.matches("^[0-9]+m?$")) { // Allows "1" or "100m"
      Some("CPU Units must be a number (e.g., '1' or '100m')")
    } else if (memSize.isEmpty || !memSize.matches("^[0-9]+(Mi|Gi)$")) { // Allows "100Mi", "4Gi"
      Some("Memory Size must be a number followed by Mi or Gi (e.g., '100Mi' or '4Gi')")
    } else if (gpuNum < 0) {
      Some("GPU count cannot be negative")
    } else {
      None
    }
  }

  def resetForm(): Unit = {
    nameVar.set("")
    descriptionVar.set("")
    selectedSpaceIdVar.set(None) // Clear selected space
    cpuUnitsVar.set("1")
    memSizeVar.set("4Gi")
    gpuNumVar.set(0)
    errorVar.set(None)
  }

  def submitForm(): Unit = {
    validateForm() match {
      case Some(error) =>
        errorVar.set(Some(error))
      case None =>
        isSubmittingVar.set(true)
        errorVar.set(None)

        val spaceId = selectedSpaceIdVar.now().get // We validated it's not empty

        // Create NodeSpecs based on user input
        val nodes = NodeSpecs(
          cpu = Some(CpuSpec(units = cpuUnitsVar.now().trim)),
          mem = Some(MemSpec(size = memSizeVar.now().trim)),
          gpu = if (gpuNumVar.now() > 0) Some(GpuSpec(num_gpu = gpuNumVar.now())) else None
        )

        val request = HardwareSpecificationCreate(
          name = nameVar.now().trim,
          description = Some(descriptionVar.now().trim).filter(_.nonEmpty),
          nodes = nodes
        )

        basicRequest
          .post(uri"/api/watsonxai/hardware_specs?space_id=${spaceId}") // Your server endpoint
          .body(request)
          .response(asJson[HardwareSpecificationResource])
          .send(backend)
          .map(_.body)
          .foreach {
            case Right(resource) =>
              isSubmittingVar.set(false)
              modal.close()
              resetForm()
              NotificationManager.showSuccess(
                s"Successfully created hardware specification: ${resource.metadata.name}",
                "Specification Created"
              )
              onSuccess()
            case Left(err) =>
              isSubmittingVar.set(false)
              errorVar.set(Some(s"Failed to create hardware specification: $err"))
              NotificationManager.showError(
                s"Failed to create specification: $err",
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

    // Deployment Space Dropdown
    div(
      className := "cds--form-item",
      cds"dropdown"(
        strattr("title-text") := "Select Deployment Space",
        strattr("helper-text") := "Choose the deployment space where this hardware specification will be created.",
        strattr("label") := "Deployment Space",
        strattr("value") <-- selectedSpaceIdVar.signal.map(_.getOrElse("")),

        eventProp[dom.CustomEvent]("cds-dropdown-selected").map { event =>
          val detail = event.detail.asInstanceOf[js.Dynamic]
          val selectedValue = detail.item.value.asInstanceOf[String]
          selectedValue
        } --> onDeploymentSpaceSelect,
        children <-- Signal.combine(isLoadingDeploymentSpaces.signal, deploymentSpaces.signal, selectedSpaceIdVar.signal).map {
          case (isLoading, spaces, selectedId) =>
            if (isLoading) {
              List(cds"dropdown-item"(value := "", disabled := true, "Loading spaces..."))
            } else if (spaces.isEmpty) {
              List(cds"dropdown-item"(value := "", disabled := true, "No active deployment spaces found"))
            } else {
              val defaultOption = if (selectedId.isEmpty) {
                List(cds"dropdown-item"(value := "", disabled := true, selected := true, "Select a space..."))
              } else {
                List.empty[HtmlElement]
              }
              val spaceOptions = spaces.map { space =>
                cds"dropdown-item"(
                  value := space.metadata.id,
                  s"${space.entity.name} (ID: ${space.metadata.id})"
                )
              }
              defaultOption ++ spaceOptions
            }
        }
      )
    ),


    // Name input
    div(
      className := "cds--form-item",
      label(
        className := "cds--label",
        forId := "spec-name",
        "Specification Name"
      ),
      input(
        className := "cds--text-input",
        typ := "text",
        idAttr := "spec-name",
        placeholder := "e.g. custom_hw_spec_2cpu_10Gi",
        controlled(
          value <-- nameVar,
          onInput.mapToValue --> nameVar
        )
      ),
      div(
        className := "cds--form__helper-text",
        "A unique name for your custom hardware specification."
      )
    ),

    // Description input
    div(
      className := "cds--form-item",
      label(
        className := "cds--label",
        forId := "spec-description",
        "Description (Optional)"
      ),
      input(
        className := "cds--text-input",
        typ := "text",
        idAttr := "spec-description",
        placeholder := "e.g. Custom spec with 2 CPU and 10Gi RAM",
        controlled(
          value <-- descriptionVar,
          onInput.mapToValue --> descriptionVar
        )
      ),
      div(
        className := "cds--form__helper-text",
        "A brief description of this hardware specification."
      )
    ),

    // --- Node Specs Inputs ---
    h4("Node Resources"),
    // CPU Units
    div(
      className := "cds--form-item",
      cds"number-input"(
        strattr("title-text") := "CPU Units",
        strattr("helper-text") := "Number of CPU units (e.g., '1' for 1 CPU, '500m' for 0.5 CPU).",
        idAttr := "cpu-units-input",
        // controlled requires value and onInput/onChange. CDS number input uses value and change.
        // Convert number to string for display and back for model.
        eventProp[dom.CustomEvent]("cds-number-input-changed").map{ event =>
        val detail = event.detail.asInstanceOf[js.Dynamic]
        detail.value.asInstanceOf[String]
        } --> cpuUnitsVar.writer,
        strattr("min") := "0m" // Minimum value, can be "0m" for millicores
      )
    ),
    // Memory Size
    div(
      className := "cds--form-item",
      cds"number-input"( // Re-using cds-number-input, but user types string like "4Gi"
        strattr("title-text") := "Memory Size",
        strattr("helper-text") := "Amount of RAM (e.g., '4Gi', '512Mi').",
        idAttr := "mem-size-input",
        eventProp[dom.CustomEvent]("cds-number-input-changed").map { event =>
          val detail = event.detail.asInstanceOf[js.Dynamic]
          detail.value.asInstanceOf[String]
        } --> memSizeVar.writer
        // Add min/max attributes if cds-number-input supports string validation
        // strattr("min") := "1Mi" // Example min
      )
    ),
    // GPU Count
    div(
      className := "cds--form-item",
      cds"number-input"(
        strattr("title-text") := "GPU Count",
        strattr("helper-text") := "Number of GPUs.",
        idAttr := "gpu-num-input",
        strattr("value") <-- gpuNumVar.signal.map(_.toString), // Convert Int to String
        onInput.mapToValue.filter(_.nonEmpty).map(_.toIntOption.getOrElse(0)) --> gpuNumVar.writer, // Convert String to Int
        strattr("min") := "0"
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
          selectedSpaceIdVar.signal,
          cpuUnitsVar.signal,
          memSizeVar.signal,
          gpuNumVar.signal
        ).map { case (isSubmitting, name, spaceId, cpu, mem, gpu) =>
          isSubmitting || name.trim.isEmpty || spaceId.isEmpty || cpu.trim.isEmpty || mem.trim.isEmpty || gpu < 0
        },
        onClick --> { _ => submitForm() },
        child.text <-- isSubmittingVar.signal.map(if (_) "Creating..." else "Create Specification")
      )
    )
  )

  def onDeploymentSpaceSelect(selectionId: String): Unit = {}
  // Create the modal with the form as content
  private lazy val modal = new Modal(
    title = "Create Custom Hardware Specification",
    subtitle = "Define custom CPU, memory, and GPU resources for your models.",
    content = formContent,
    size = "lg",
    id = Some("create-hardware-spec-modal")
  )

  // Public methods
  def open(): Unit = {
    fetchDeploymentSpaces()
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
object CreateHardwareSpecModal extends Component {
  def apply(onSuccess: () => Unit): CreateHardwareSpecModal = new CreateHardwareSpecModal(onSuccess)
}
