package org.ibm.client.components.foundationmodels

import com.raquo.laminar.api.L.*
import org.ibm.client.components.notifications.NotificationManager
import org.ibm.client.components.{Component, cds}
import org.ibm.shared._
import org.ibm.tel.components.Modal // Assuming this is your Modal component
import sttp.capabilities.WebSockets
import sttp.client3.*
import sttp.client3.circe.*
import io.circe.Json

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import java.time.Instant
import java.util.UUID
import org.scalajs.dom // Explicitly import dom
import scala.scalajs.js // Explicitly import js for dynamic casting


class CreateFoundationModelModal(onSuccess: () => Unit) extends Component {

  val backend: SttpBackend[Future, WebSockets] = FetchBackend()

  // Form fields
  val selectedModelIdVar = Var[Option[String]](None) // New: Tracks the ID of the selected DownloadedModel
  val modelIdVar = Var("") // User-editable Model ID (can be auto-populated)
  val subPathVar = Var("") // User-editable Sub Path (can be auto-populated)
  val pvcNameVar = Var("") // User-editable PVC Name (can be auto-populated)
  val tagsVar = Var("") // Tags as comma-separated string for UI
  val parametersVar = Var[List[ModelParameter]](List.empty)

  // State
  val isSubmittingVar = Var(false)
  val pvcs = Var[List[PVC]](List.empty)
  val downloadedModels = Var[List[DownloadedModel]](List.empty) // New: List of downloaded models
  val errorVar = Var[Option[String]](None)
  val isLoadingPvcs = Var(true)
  val isLoadingDownloadedModels = Var(true) // New: Loading state for downloaded models

  // Parameter management (no change)
  case class ParameterTemplate(
                                name: String,
                                paramType: String, // "string", "number"
                                defaultValue: String,
                                options: Option[List[String]] = None,
                                min: Option[Double] = None,
                                max: Option[Double] = None,
                                description: String
                              )

  val availableParameters = List(
    ParameterTemplate("dtype", "string", "float16", Some(List("float16", "bfloat16")), None, None,
      "Data type for your model"),
    ParameterTemplate("max_batch_size", "number", "256", None, Some(1.0), None,
      "Maximum batch size for your model"),
    ParameterTemplate("max_concurrent_requests", "number", "1024", None, Some(1.0), None,
      "Maximum number of concurrent requests"),
    ParameterTemplate("max_new_tokens", "number", "2048", None, Some(20.0), None,
      "Maximum number of tokens that can be generated"),
    ParameterTemplate("max_sequence_length", "number", "2048", None, Some(20.0), None,
      "Maximum sequence length for your model")
  )

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
        // Don't auto-select PVC here, it will be handled by model selection or user override
        case Left(err) =>
          errorVar.set(Some(s"Failed to load PVCs: $err"))
          isLoadingPvcs.set(false)
      }
  }

  // New: Fetch downloaded models from your backend
  def fetchDownloadedModels(): Unit = {
    isLoadingDownloadedModels.set(true)
    basicRequest
      .get(uri"/api/downloaded_models") // <--- Your new API endpoint
      .response(asJson[List[DownloadedModel]])
      .send(backend)
      .map(_.body)
      .foreach {
        case Right(modelsResponse) =>
          // Filter for COMPLETED models only
          println("found models")
          println(modelsResponse)
          downloadedModels.set(modelsResponse)
          isLoadingDownloadedModels.set(false)
        case Left(err) =>
          errorVar.set(Some(s"Failed to load downloaded models: $err"))
          isLoadingDownloadedModels.set(false)
      }
  }

  // New: Handler for when a model is selected from the dropdown
  def onModelSelect(selectedId: String): Unit = {
    selectedModelIdVar.set(Some(selectedId))
    downloadedModels.now().find(_.id == selectedId).foreach { model =>
      // 4. Normalize model name logic
      val modelRepoParts = model.modelRepo.split("_", 2) // Split only on the first underscore
      // val huggingFaceRepoOwner = modelRepoParts.headOption.getOrElse("") // Unused, can remove
      val actualModelName = modelRepoParts.lift(1).getOrElse(model.modelRepo) // Fallback to full repo if no underscore

      // 5. In a typical scenario, localDirName is the model name itself
      val recoveredLocalDirName = if (model.localDirName == "unknown" || model.localDirName.isEmpty) {
        actualModelName
      } else {
        model.localDirName
      }

      modelIdVar.set(model.modelRepo) // Full normalized repo name for model_id
      subPathVar.set(recoveredLocalDirName) // Use recovered local directory name
      pvcNameVar.set(model.pvcName) // Auto-populate PVC name
    }
  }


  def validateForm(): Option[String] = {
    val modelId = modelIdVar.now().trim
    val subPath = subPathVar.now().trim
    val pvcName = pvcNameVar.now().trim

    if (modelId.isEmpty) {
      Some("Model ID is required")
    } else if (subPath.isEmpty) {
      Some("Sub path is required")
    } else if (pvcName.isEmpty) {
      Some("PVC selection is required")
    } else if (!modelId.matches("^[a-zA-Z0-9][a-zA-Z0-9/_.-]*[a-zA-Z0-9]$")) {
      Some("Model ID contains invalid characters. Use alphanumeric, _, ., - only, and start/end with alphanumeric.")
    } else {
      // Validate parameters (no change)
      val params = parametersVar.now()
      val maxBatchSize = params.find(_.name == "max_batch_size").flatMap(p =>
        getDoubleFromJson(p.default))
      val maxConcurrentRequests = params.find(_.name == "max_concurrent_requests").flatMap(p =>
        getDoubleFromJson(p.default))
      val maxNewTokens = params.find(_.name == "max_new_tokens").flatMap(p =>
        getDoubleFromJson(p.default))
      val maxSequenceLength = params.find(_.name == "max_sequence_length").flatMap(p =>
        getDoubleFromJson(p.default))

      (maxBatchSize, maxConcurrentRequests) match {
        case (Some(batch), Some(concurrent)) if concurrent < batch =>
          Some("max_concurrent_requests must be >= max_batch_size")
        case _ =>
          (maxNewTokens, maxSequenceLength) match {
            case (Some(newTokens), Some(seqLength)) if seqLength <= newTokens =>
              Some("max_sequence_length must be > max_new_tokens")
            case _ => None
          }
      }
    }
  }

  // Helper method to extract double from Json (no change)
  def getDoubleFromJson(json: Json): Option[Double] = {
    json.asNumber.map(x => x.toDouble).orElse(json.asString.flatMap(_.toDoubleOption))
  }

  // Helper method to get string from Json (no change)
  def getStringFromJson(json: Json): String = {
    json.asString.getOrElse(json.asNumber.map(_.toString).getOrElse(""))
  }

  def resetForm(): Unit = {
    selectedModelIdVar.set(None) // New: Clear selected model
    modelIdVar.set("")
    subPathVar.set("")
    pvcNameVar.set("")
    tagsVar.set("")
    parametersVar.set(List.empty)
    errorVar.set(None)
  }

  // Parameter management methods (no change)
  def addParameter(template: ParameterTemplate): Unit = {
    val currentParams = parametersVar.now()
    if (!currentParams.exists(_.name == template.name)) {
      val defaultValue = template.paramType match {
        case "number" => Json.fromDoubleOrNull(template.defaultValue.toDouble)
        case _ => Json.fromString(template.defaultValue)
      }

      val newParam = ModelParameter(
        name = template.name,
        default = defaultValue,
        options = template.options,
        min = template.min,
        max = template.max
      )
      parametersVar.set(currentParams :+ newParam)
    }
  }

  def removeParameter(paramName: String): Unit = {
    parametersVar.update(_.filter(_.name != paramName))
  }

  def updateParameterValue(paramName: String, newValue: String): Unit = {
    val params = parametersVar.now()
    val paramIndex = params.indexWhere(_.name == paramName)
    if (paramIndex >= 0) {
      val jsonValue = newValue.toDoubleOption match {
        case Some(d) => Json.fromDoubleOrNull(d)
        case None => Json.fromString(newValue)
      }
      val param = params(paramIndex).copy(default = jsonValue)
      parametersVar.set(params.updated(paramIndex, param))
    }
  }

  def submitForm(): Unit = {
    validateForm() match {
      case Some(error) =>
        errorVar.set(Some(error))
      case None =>
        isSubmittingVar.set(true)
        errorVar.set(None)

        val tags = tagsVar.now().split(",").map(_.trim).filter(_.nonEmpty).toList

        // Create CustomFoundationModel with correct field names
        val request = CustomFoundationModel(
          model_id = modelIdVar.now().trim,
          location = ModelLocation(
            pvc_name = pvcNameVar.now().trim,
            sub_path = subPathVar.now().trim
          ),
          tags = tags,
          parameters = parametersVar.now()
        )

        basicRequest
          .post(uri"/api/watsonxaiifm")
          .body(request)
          .response(asJson[WatsonxAIIFM])
          .send(backend)
          .map(_.body)
          .foreach {
            case Right(watsonxAI) =>
              isSubmittingVar.set(false)
              modal.close()
              resetForm()
              NotificationManager.showSuccess(
                s"Successfully registered foundation model: ${request.model_id}",
                "Model Registered"
              )
              onSuccess()
            case Left(err) =>
              isSubmittingVar.set(false)
              errorVar.set(Some(s"Failed to register foundation model: $err"))
              NotificationManager.showError(
                s"Failed to register model: $err",
                "Registration Failed"
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

    // --- NEW: Downloaded Model Selection Dropdown ---
    div(
      className := "cds--form-item",
      cds"dropdown"(
        strattr("title-text") := "Select Downloaded Model",
        strattr("helper-text") := "Choose a previously downloaded model to pre-populate registration fields.",
        strattr("label") := "Downloaded Model",
        // 1. Set the value attribute directly from the signal
        strattr("value") <-- selectedModelIdVar.signal.map(_.getOrElse("")),
        // 2. Listen to the custom event emitted by cds-dropdown
        eventProp[dom.CustomEvent]("cds-dropdown-selected").map { event =>
          val detail = event.detail.asInstanceOf[js.Dynamic]
          val selectedValue = detail.item.value.asInstanceOf[String]
          selectedValue
        } --> onModelSelect,
        // *** START FIX: Combine signals for unified rendering ***
        children <-- Signal.combine(isLoadingDownloadedModels.signal, downloadedModels.signal, selectedModelIdVar.signal).map {
          case (isLoading, models, selectedModelId) =>
            if (isLoading) {
              List(cds"dropdown-item"(value := "", disabled := true, "Loading models..."))
            } else if (models.isEmpty && !isLoading) {
              List(cds"dropdown-item"(value := "", disabled := true, "No downloaded models found"))
            } else {
              // Add a default "Select a model..." option if nothing is selected
              val defaultOption = if (selectedModelId.isEmpty) {
                List(cds"dropdown-item"(value := "", disabled := true, selected := true, "Select a model..."))
              } else {
                List.empty[HtmlElement]
              }
              // Render actual model items
              val modelOptions = models.map { model =>
                cds"dropdown-item"(
                  value := model.id,
                  s"${model.modelRepo} (PVC: ${model.pvcName}, Path: ${model.localDirName})"
                )
              }
              defaultOption ++ modelOptions
            }
        }
        // *** END FIX: Combine signals for unified rendering ***
      )
    ),

    // Model ID input (now can be auto-populated and overridden)
    div(
      className := "cds--form-item",
      label(
        className := "cds--label",
        forId := "model-id",
        "Model ID (HuggingFace Repo)"
      ),
      input(
        className := "cds--text-input",
        typ := "text",
        idAttr := "model-id",
        placeholder := "e.g. TheBloke/Mistral-7B-Instruct-v0.2-GGU",
        controlled(
          value <-- modelIdVar,
          onInput.mapToValue --> modelIdVar
        )
      ),
      div(
        className := "cds--form__helper-text",
        "Unique identifier for your foundation model, usually the HuggingFace repository name."
      )
    ),

    // Sub Path input (now can be auto-populated and overridden)
    div(
      className := "cds--form-item",
      label(
        className := "cds--label",
        forId := "sub-path",
        "Sub Path (Model Directory)"
      ),
      input(
        className := "cds--text-input",
        typ := "text",
        idAttr := "sub-path",
        placeholder := "e.g. Mistral-7B-Instruct-v0.2-GGU",
        controlled(
          value <-- subPathVar,
          onInput.mapToValue --> subPathVar
        )
      ),
      div(
        className := "cds--form__helper-text",
        "Directory path within the PVC where the model is stored. Typically the model's short name."
      )
    ),

    // PVC Selection input (now can be auto-populated and overridden)
    // We'll use a standard Carbon text input here as the dropdown is for *downloaded models*
    // If you want a dropdown for PVCs, that's a separate component.
    div(
      className := "cds--form-item",
      label(
        className := "cds--label",
        forId := "pvc-name-input",
        "Storage PVC"
      ),
      input(
        className := "cds--text-input",
        typ := "text",
        idAttr := "pvc-name-input",
        placeholder := "e.g. model-storage",
        controlled(
          value <-- pvcNameVar,
          onInput.mapToValue --> pvcNameVar
        )
      ),
      div(
        className := "cds--form__helper-text",
        "The Persistent Volume Claim where the model files are stored. This can be pre-filled from downloaded models."
      )
    ),

    // Tags input (no change)
    div(
      className := "cds--form-item",
      label(
        className := "cds--label",
        forId := "tags",
        "Tags (Optional)"
      ),
      input(
        className := "cds--text-input",
        typ := "text",
        idAttr := "tags",
        placeholder := "e.g. llama, 7b, gptq",
        controlled(
          value <-- tagsVar,
          onInput.mapToValue --> tagsVar
        )
      ),
      div(
        className := "cds--form__helper-text",
        "Comma-separated tags for organizing your models"
      )
    ),

    // Parameters section (no change, other than `children <--` for options)
    div(
      className := "cds--form-item",
      label(
        className := "cds--label",
        "Model Parameters (Optional)"
      ),

      // Add parameter dropdown
      div(
        className := "parameter-controls",
        marginBottom := "1rem",
        label("Add Parameter:"),
        div(
          className := "cds--select",
          select(
            className := "cds--select-input",
            onChange.mapToValue --> { paramName =>
              if (paramName.nonEmpty) {
                availableParameters.find(_.name == paramName).foreach(addParameter)
              }
            },
            option(value := "", "Select parameter to add..."),
            children <-- parametersVar.signal.map { currentParams =>
              availableParameters
                .filterNot(template => currentParams.exists(_.name == template.name))
                .map { template =>
                  option(
                    value := template.name,
                    s"${template.name} - ${template.description}"
                  )
                }
            }
          ),
          svg.svg(
            svg.className := "cds--select__arrow",
            svg.width := "16",
            svg.height := "16",
            svg.viewBox := "0 0 16 16",
            svg.fill := "currentColor",
            svg.path(svg.d := "m8 11L3 6l0.7-0.7L8 9.6l4.3-4.3L13 6z")
          )
        )
      ),

      // Current parameters list
      div(
        className := "parameters-list",
        child <-- parametersVar.signal.map { params =>
          if (params.isEmpty) {
            div(
              className := "no-parameters",
              fontStyle := "italic",
              color := "#6f6f6f",
              "No parameters added. Parameters are optional but can help optimize your model's performance."
            )
          } else {
            div(
              params.map { param =>
                val template = availableParameters.find(_.name == param.name)
                div(
                  className := "parameter-item",
                  border := "1px solid #e0e0e0",
                  borderRadius := "4px",
                  padding := "1rem",
                  marginBottom := "0.5rem",
                  backgroundColor := "#fafafa",

                  div(
                    className := "parameter-header",
                    display := "flex",
                    justifyContent := "space-between",
                    alignItems := "center",
                    marginBottom := "0.5rem",

                    div(
                      strong(param.name),
                      span(
                        marginLeft := "0.5rem",
                        fontSize := "0.875rem",
                        color := "#6f6f6f",
                        template.map(t => s"- ${t.description}").getOrElse("")
                      )
                    )
                    ,
                    button(
                      className := "cds--btn cds--btn--sm cds--btn--ghost",
                      onClick --> { _ => removeParameter(param.name) },
                      "Remove"
                    )
                  ),

                  div(
                    className := "parameter-controls",
                    display := "flex",
                    gap := "1rem",
                    alignItems := "center",

                    // Value input/select
                    template match {
                      case Some(t) if t.options.isDefined =>
                        // Dropdown for options
                        div(
                          className := "cds--select",
                          width := "200px",
                          select(
                            className := "cds--select-input",
                            value := getStringFromJson(param.default),
                            onChange.mapToValue --> { value =>
                              updateParameterValue(param.name, value)
                            },
                            t.options.get.map { optionValue =>
                              option(
                                value := optionValue,
                                selected := getStringFromJson(param.default) == optionValue,
                                optionValue
                              )
                            }
                          )
                        )
                      case _ =>
                        // Text input
                        input(
                          className := "cds--text-input",
                          typ := "text",
                          value := getStringFromJson(param.default),
                          onInput.mapToValue --> { value =>
                            updateParameterValue(param.name, value)
                          },
                          width := "200px"
                        )
                    },

                    // Min/Max display
                    template.flatMap(_.min).map { min =>
                      span(
                        fontSize := "0.75rem",
                        color := "#6f6f6f",
                        s"Min: $min"
                      )
                    },

                    template.flatMap(_.max).map { max =>
                      span(
                        fontSize := "0.875rem",
                        color := "#6f6f6f",
                        s"Max: $max"
                      )
                    }
                  )
                )
              }
            )
          }
        }
      )
    ),

    // Modal footer (no change)
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
          modelIdVar.signal,
          subPathVar.signal,
          pvcNameVar.signal
        ).map { case (isSubmitting, modelId, subPath, pvcName) =>
          isSubmitting || modelId.trim.isEmpty || subPath.trim.isEmpty || pvcName.trim.isEmpty
        },
        onClick --> { _ => submitForm() },
        child.text <-- isSubmittingVar.signal.map(if (_) "Registering..." else "Register Model")
      )
    )
  )

  // Create the modal with the form as content (no change)
  private lazy val modal = new Modal(
    title = "Register Foundation Model",
    subtitle = "Register a custom foundation model with WatsonX AI. Select a downloaded model or manually enter details.",
    content = formContent,
    size = "lg",
    id = Some("create-foundation-model-modal")
  )

  // Public methods
  def open(): Unit = {
    // New: Fetch downloaded models when modal opens
    fetchDownloadedModels()
    fetchPvcs()
    modal.open()
  }

  def close(): Unit = {
    modal.close()
    resetForm()
  }

  // Expose the element
  val element: Element = modal.element
}

// Companion object for easy creation (no change)
object CreateFoundationModelModal extends Component {
  def apply(onSuccess: () => Unit): CreateFoundationModelModal = new CreateFoundationModelModal(onSuccess)
}
