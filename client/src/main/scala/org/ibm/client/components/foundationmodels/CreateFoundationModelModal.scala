package org.ibm.client.components.foundationmodels

import com.raquo.laminar.api.L.*
import org.ibm.client.components.notifications.NotificationManager
import org.ibm.client.components.{Component, cds}
import org.ibm.shared._
import org.ibm.tel.components.Modal
import sttp.capabilities.WebSockets
import sttp.client3.*
import sttp.client3.circe.*
import io.circe.Json

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

class CreateFoundationModelModal(onSuccess: () => Unit) extends Component {

  val backend: SttpBackend[Future, WebSockets] = FetchBackend()

  // Form fields - Updated for CustomFoundationModel
  val modelIdVar = Var("")
  val subPathVar = Var("")
  val pvcNameVar = Var("") // Changed to match pvc_name
  val tagsVar = Var("") // Tags as comma-separated string for UI
  val parametersVar = Var[List[ModelParameter]](List.empty)

  // State
  val isSubmittingVar = Var(false)
  val pvcs = Var[List[PVC]](List.empty)
  val errorVar = Var[Option[String]](None)
  val isLoadingPvcs = Var(true)

  // Parameter management
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
          if (pvcsResponse.nonEmpty && pvcNameVar.now().isEmpty) {
            pvcNameVar.set(pvcsResponse.head.metadata.name)
          }
        case Left(err) =>
          errorVar.set(Some(s"Failed to load PVCs: $err"))
          isLoadingPvcs.set(false)
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
      Some("Model ID contains invalid characters")
    } else {
      // Validate parameters
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

  // Helper method to extract double from Json
  def getDoubleFromJson(json: Json): Option[Double] = {
    json.asNumber.map(x => x.toDouble).orElse(json.asString.flatMap(_.toDoubleOption))
  }

  // Helper method to get string from Json
  def getStringFromJson(json: Json): String = {
    json.asString.getOrElse(json.asNumber.map(_.toString).getOrElse(""))
  }

  def resetForm(): Unit = {
    modelIdVar.set("")
    subPathVar.set("")
    pvcNameVar.set("")
    tagsVar.set("")
    parametersVar.set(List.empty)
    errorVar.set(None)
  }

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
            pvc_name = pvcNameVar.now().trim, // Note: pvc_name not pvcName
            sub_path = subPathVar.now().trim   // Note: sub_path not subPath
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

    // Model ID input
    div(
      className := "cds--form-item",
      label(
        className := "cds--label",
        forId := "model-id",
        "Model ID"
      ),
      input(
        className := "cds--text-input",
        typ := "text",
        idAttr := "model-id",
        placeholder := "e.g. TheBloke/CapybaraHermes-2.5-Mistral-7B-GPTQ",
        controlled(
          value <-- modelIdVar,
          onInput.mapToValue --> modelIdVar
        )
      ),
      div(
        className := "cds--form__helper-text",
        "Unique identifier for your foundation model"
      )
    ),

    // Sub Path input
    div(
      className := "cds--form-item",
      label(
        className := "cds--label",
        forId := "sub-path",
        "Sub Path"
      ),
      input(
        className := "cds--text-input",
        typ := "text",
        idAttr := "sub-path",
        placeholder := "e.g. CapybaraHermes-2.5-Mistral-7B-GPTQ",
        controlled(
          value <-- subPathVar,
          onInput.mapToValue --> subPathVar
        )
      ),
      div(
        className := "cds--form__helper-text",
        "Directory path within the PVC where the model is stored"
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
                value <-- pvcNameVar,
                onChange.mapToValue --> pvcNameVar
              ),
              option(
                value := "",
                disabled := true,
                "Select PVC for storage"
              ),
              pvcList.map { pvc =>
                option(
                  value := pvc.metadata.name,
                  pvc.metadata.name
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
        "PVC where the model files are stored"
      )
    ),

    // Tags input
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

    // Parameters section
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
            // FIXED: Use children <-- instead of child <-- for lists
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
                    ),

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
                            // FIXED: Use option instead of optionTag
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
                        fontSize := "0.75rem",
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

  // Create the modal with the form as content
  private lazy val modal = new Modal(
    title = "Register Foundation Model",
    subtitle = "Register a custom foundation model with WatsonX AI. You must have previously downloaded the model and stored it in a PVC.",
    content = formContent,
    size = "lg",
    id = Some("create-foundation-model-modal")
  )

  // Public methods
  def open(): Unit = {
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

// Companion object for easy creation
object CreateFoundationModelModal extends Component {
  def apply(onSuccess: () => Unit): CreateFoundationModelModal = new CreateFoundationModelModal(onSuccess)
}
