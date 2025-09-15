package org.ibm.watsonxaiifm

import io.kubernetes.client.openapi.apis.CustomObjectsApi
import io.kubernetes.client.util.Config
import org.ibm.shared.{CustomFoundationModel, ModelLocation, ModelParameter, WatsonxAIIFM}
import io.circe.parser.*
import io.circe.syntax.*
import io.circe.{Decoder, Encoder, Json}
import io.circe.generic.semiauto.*
import com.fasterxml.jackson.databind.ObjectMapper
import io.kubernetes.client.custom.V1Patch
import io.kubernetes.client.openapi.ApiException

import scala.util.{Failure, Success, Try}
import scala.jdk.CollectionConverters.*

object Client {
  // 1) Setup Kubernetes API client and Jackson mapper
  private val apiClient = Config.defaultClient()
  private val customObjectsApi = new CustomObjectsApi(apiClient)
  private val jackson = new ObjectMapper()

  // Constants for the CR
  private val GROUP = "watsonxaiifm.cpd.ibm.com"
  private val VERSION = "v1beta1"
  private val NAMESPACE = "cpd"
  private val PLURAL = "watsonxaiifm"
  private val NAME = "watsonxaiifm-cr"

  // 2) Fetch the CR as a raw JSON string
  def fetchRawJson(): Try[String] = Try {
    val obj: java.lang.Object = customObjectsApi.getNamespacedCustomObject(
      GROUP, VERSION, NAMESPACE, PLURAL, NAME
    ).execute()
    jackson.writeValueAsString(obj)
  }

  // 3) Parse into your domain model with Circe
  def getWatsonxAIIFM: Try[WatsonxAIIFM] = fetchRawJson().flatMap { json =>
    parse(json)
      .flatMap(_.as[WatsonxAIIFM])
      .fold(
        err => Failure(new RuntimeException(s"Circe parse failed: $err\nRaw JSON: $json")),
        success => Success(success)
      )
  }

  // 4) Get current custom foundation models
  def getCustomFoundationModels: Try[List[CustomFoundationModel]] = {
    fetchRawJson().flatMap { json =>
      parse(json).flatMap { jsonObj =>
        jsonObj.hcursor
          .downField("spec")
          .downField("custom_foundation_models")
          .as[List[CustomFoundationModel]]
      }.fold(
        err => Failure(new RuntimeException(s"Failed to parse custom foundation models: $err")),
        models => Success(models)
      )
    }
  }

  // 5) Add a new custom foundation model
  def addCustomFoundationModel(newModel: CustomFoundationModel): Try[Unit] = {
    for {
      currentModels <- getCustomFoundationModels

      // Check if model already exists
      _ <- if (currentModels.exists(_.model_id == newModel.model_id)) {
        Failure(new RuntimeException(s"Model with ID '${newModel.model_id}' already exists"))
      } else {
        Success(())
      }

      // Update the models list
      updatedModels = currentModels :+ newModel
      result <- updateCustomFoundationModels(updatedModels)
    } yield result
  }

  /** Public API: remove the model with `modelId` from the array */
  def removeCustomFoundationModel(modelId: String): Try[Unit] = {
    for {
      cr <- getWatsonxAIIFM // fetch domain model
      filtered = cr.spec.custom_foundation_models.filterNot(_.model_id == modelId)
      _ <- patchCustomModelsList(filtered) // apply patch
    } yield ()
  }

  /** Wraps your Seq[CustomModel] in a strategic-merge patch and sends it */
  private def patchCustomModelsList(
                                     newList: Seq[CustomFoundationModel]
                                   ): Try[Unit] = Try {
    // Build the minimal JSON-merge-patch document:
    // { "spec": { "custom_foundation_models": [ … your newList … ] } }
    val patchJson = io.circe.Json.obj(
      "spec" -> io.circe.Json.obj(
        "custom_foundation_models" -> newList.asJson
      )
    ).noSpaces

    val patch = new V1Patch(patchJson)

    try {
      customObjectsApi.patchNamespacedCustomObject(
        GROUP,
        VERSION,
        NAMESPACE,
        PLURAL,
        NAME,
        patch, // body
      )
      println(s"✅ Patched spec.custom_foundation_models with ${newList.size} entries")
    } catch {
      case ex: ApiException =>
        throw new RuntimeException(
          s"Patching failed [code=${ex.getCode}]: ${ex.getResponseBody}", ex
        )
    }
  }
    private def findModelIndex(
                                models: Seq[CustomFoundationModel],
                                id: String
                              ): Try[Int] =
      models.indexWhere(_.model_id == id) match {
        case -1 => Failure(new RuntimeException(s"No model with ID ‘$id’"))
        case index => Success(index)
      }


    /** Fallback: treat the patch JSON as a strategic‐merge patch */
    private def patchViaStrategicMerge(patchJson: String): Unit = {
      val mergeJson =
        s"""{ "spec": { "custom_foundation_models": $patchJson } }"""
      val mergePatch = new V1Patch(mergeJson)
      customObjectsApi.patchNamespacedCustomObject(
        Client.GROUP,
        Client.VERSION,
        Client.NAMESPACE,
        Client.PLURAL,
        Client.NAME,
        mergePatch
      )
      println("✅ Applied Strategic Merge Patch")
    }
  private def applyStrategicMergePatch(jsonPatch: Json): Unit = {
    // For strategic merge patch, we need to convert the patch to a merge format
    // This is a simplified conversion - you might need to handle this differently
    // depending on your specific requirements

    println("Falling back to full array replacement...")
    // Get current models and apply the change manually, then use strategic merge
    val currentModels = getCustomFoundationModels match {
      case Failure(exception) => ???
      case Success(value) => {
        val mergePatch = Json.obj(
          "spec" -> Json.obj(
            "custom_foundation_models" -> value.asJson
          )
        )
        val patchMap = jsonToJavaMap(mergePatch)

        customObjectsApi.patchNamespacedCustomObject(
          GROUP,
          VERSION,
          NAMESPACE,
          PLURAL,
          NAME,
          patchMap, // Strategic Merge Patch format
        ).execute()
        
      }
    }
    

  }
  // 7) Update a custom foundation model
  def updateCustomFoundationModel(modelId: String, updatedModel: CustomFoundationModel): Try[Unit] = {
    for {
      currentModels <- getCustomFoundationModels

      // Check if model exists
      modelIndex <- currentModels.indexWhere(_.model_id == modelId) match {
        case -1 => Failure(new RuntimeException(s"Model with ID '$modelId' not found"))
        case index => Success(index)
      }

      // Replace the model
      updatedModels = currentModels.updated(modelIndex, updatedModel.copy(model_id = modelId))
      result <- updateCustomFoundationModels(updatedModels)
    } yield result
  }

  // 8) Update using proper JSON Patch format - FIXED
  def updateCustomFoundationModels(models: List[CustomFoundationModel]): Try[Unit] = Try {
    // Create a proper JSON Patch (RFC 6902) with replace operation
    val jsonPatch = Json.arr(
      Json.obj(
        "op" -> Json.fromString("replace"),
        "path" -> Json.fromString("/spec/custom_foundation_models"),
        "value" -> models.asJson
      )
    )

    // Convert to Java List for the patch operation
    val patchString = jsonPatch.noSpaces
    val patchList = jackson.readValue(patchString, classOf[java.util.List[Object]])

    println(s"Sending JSON Patch: $patchString")

    // Perform the JSON patch operation
    customObjectsApi.patchNamespacedCustomObject(
      GROUP,
      VERSION,
      NAMESPACE,
      PLURAL,
      NAME,
      patchList,     // Send as List[Object] for JSON Patch
    ).execute()

    println(s"Successfully updated custom foundation models using JSON Patch. Total models: ${models.length}")
  }

  // 9) Alternative: Use Strategic Merge Patch (Kubernetes-specific)
  def updateCustomFoundationModelsStrategicMerge(models: List[CustomFoundationModel]): Try[Unit] = Try {
    // Create strategic merge patch
    val mergePatch = Json.obj(
      "spec" -> Json.obj(
        "custom_foundation_models" -> models.asJson
      )
    )

    // Convert to Java Map
    val patchMap = jsonToJavaMap(mergePatch)

    println(s"Sending Strategic Merge Patch: ${mergePatch.noSpaces}")

    // Use the patch method with the Map
    customObjectsApi.patchNamespacedCustomObject(
      GROUP,
      VERSION,
      NAMESPACE,
      PLURAL,
      NAME,
      patchMap,      // Send as Map[String, Object] for Strategic Merge Patch       // force
    ).execute()

    println(s"Successfully updated custom foundation models using Strategic Merge Patch. Total models: ${models.length}")
  }

  // 10) Alternative: Use replace instead of patch
  def replaceCustomFoundationModels(models: List[CustomFoundationModel]): Try[Unit] = Try {
    // Get the current CR
    val currentCR = customObjectsApi.getNamespacedCustomObject(
      GROUP, VERSION, NAMESPACE, PLURAL, NAME
    ).execute()

    // Convert to map for modification
    val crMap = currentCR.asInstanceOf[java.util.Map[String, Object]]
    val spec = crMap.get("spec").asInstanceOf[java.util.Map[String, Object]]

    // Convert models to Java objects
    val modelsJson = models.asJson.noSpaces
    val modelsJavaList = jackson.readValue(modelsJson, classOf[java.util.List[Object]])

    // Update the spec
    spec.put("custom_foundation_models", modelsJavaList)

    println(s"Replacing CR with ${models.length} models")

    // Replace the entire CR
    customObjectsApi.replaceNamespacedCustomObject(
      GROUP,
      VERSION,
      NAMESPACE,
      PLURAL,
      NAME,
      crMap
    ).execute()

    println(s"Successfully replaced custom foundation models. Total models: ${models.length}")
  }

  // 11) Helper method to convert Circe Json to Java Map
  private def jsonToJavaMap(json: Json): java.util.Map[String, Object] = {
    val jsonString = json.noSpaces
    jackson.readValue(jsonString, classOf[java.util.Map[String, Object]])
  }

  // 12) Validate model before adding/updating
  def validateModel(model: CustomFoundationModel): Try[Unit] = Try {
    // Basic validations
    if (model.model_id.trim.isEmpty) {
      throw new RuntimeException("Model ID cannot be empty")
    }

    if (model.location.pvc_name.trim.isEmpty) {
      throw new RuntimeException("PVC name cannot be empty")
    }

    if (model.location.sub_path.trim.isEmpty) {
      throw new RuntimeException("Sub path cannot be empty")
    }

    // Validate model ID format (optional)
    if (!model.model_id.matches("^[a-zA-Z0-9][a-zA-Z0-9/_.-]*[a-zA-Z0-9]$")) {
      throw new RuntimeException("Model ID contains invalid characters")
    }

    // Validate parameters
    model.parameters.foreach { param =>
      if (param.name.trim.isEmpty) {
        throw new RuntimeException("Parameter name cannot be empty")
      }

      // Validate min/max constraints
      (param.min, param.max) match {
        case (Some(min), Some(max)) if min > max =>
          throw new RuntimeException(s"Parameter '${param.name}': min value ($min) cannot be greater than max value ($max)")
        case _ => // Valid
      }
    }

    println(s"Model validation passed for: ${model.model_id}")
  }

  // 13) Safe add with validation - Try different patch methods
  def addCustomFoundationModelSafe(model: CustomFoundationModel): Try[Unit] = {
    for {
      _ <- validateModel(model)
      result <- addCustomFoundationModel(model).recoverWith {
        case ex if ex.getMessage.contains("415") || ex.getMessage.contains("UnsupportedMediaType") =>
          println("JSON Patch failed, trying Strategic Merge Patch...")
          for {
            currentModels <- getCustomFoundationModels
            updatedModels = currentModels :+ model
            result <- updateCustomFoundationModelsStrategicMerge(updatedModels).recoverWith {
              case ex2 if ex2.getMessage.contains("415") || ex2.getMessage.contains("UnsupportedMediaType") =>
                println("Strategic Merge Patch failed, trying Replace...")
                replaceCustomFoundationModels(updatedModels)
            }
          } yield result
      }
    } yield result
  }

  // 14) Safe update with validation  
  def updateCustomFoundationModelSafe(modelId: String, updatedModel: CustomFoundationModel): Try[Unit] = {
    for {
      _ <- validateModel(updatedModel)
      result <- updateCustomFoundationModel(modelId, updatedModel)
    } yield result
  }

  // 15) List all model IDs (convenience method)
  def listModelIds: Try[List[String]] = {
    getCustomFoundationModels.map(_.map(_.model_id))
  }

  // 16) Get specific model by ID
  def getCustomFoundationModel(modelId: String): Try[CustomFoundationModel] = {
    getCustomFoundationModels.flatMap { models =>
      models.find(_.model_id == modelId) match {
        case Some(model) => Success(model)
        case None => Failure(new RuntimeException(s"Model with ID '$modelId' not found"))
      }
    }
  }
}
