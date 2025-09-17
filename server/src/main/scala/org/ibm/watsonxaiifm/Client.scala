package org.ibm.watsonxaiifm

import io.kubernetes.client.openapi.apis.CustomObjectsApi
import io.kubernetes.client.util.Config
import org.ibm.shared.{CustomFoundationModel, WatsonxAIIFM}
import io.circe.parser.*
import io.circe.syntax.*
import io.circe.Json
import com.fasterxml.jackson.databind.ObjectMapper
import io.kubernetes.client.openapi.ApiException
import okhttp3.Request
import org.apache.http.client.methods.RequestBuilder

import java.io.IOException
import scala.util.{Failure, Success, Try}

object Client {
  // Setup Kubernetes API client and Jackson mapper
  private val apiClient = Config.defaultClient()
  private val customObjectsApi = new CustomObjectsApi(apiClient)
  private val jackson = new ObjectMapper()

  // Constants for the CR
  private val GROUP = "watsonxaiifm.cpd.ibm.com"
  private val VERSION = "v1beta1"
  private val NAMESPACE = "cpd"
  private val PLURAL = "watsonxaiifm"
  private val NAME = "watsonxaiifm-cr"

  // Fetch the CR as a raw JSON string
  def fetchRawJson(): Try[String] = Try {
    val obj: java.lang.Object = customObjectsApi.getNamespacedCustomObject(
      GROUP, VERSION, NAMESPACE, PLURAL, NAME
    ).execute()
    jackson.writeValueAsString(obj)
  }

  // Parse into your domain model with Circe
  def getWatsonxAIIFM: Try[WatsonxAIIFM] = fetchRawJson().flatMap { json =>
    parse(json)
      .flatMap(_.as[WatsonxAIIFM])
      .fold(
        err => Failure(new RuntimeException(s"Circe parse failed: $err\nRaw JSON: $json")),
        success => Success(success)
      )
  }

  // Get current custom foundation models
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

  // Find the index of a model by its ID
  private def findModelIndex(modelId: String): Try[Int] = {
    getCustomFoundationModels.flatMap { models =>
      models.indexWhere(_.model_id == modelId) match {
        case -1 => Failure(new RuntimeException(s"Model with ID '$modelId' not found"))
        case index => Success(index)
      }
    }
  }

  // Remove a custom foundation model using JSON Patch (replicates kubectl patch command)
  def removeCustomFoundationModel(modelId: String): Try[Unit] = {
    for {
      index <- findModelIndex(modelId)
      result <- removeCustomFoundationModelByIndex(index)
    } yield result
  }

  // Remove a custom foundation model by index using JSON Patch
  def removeCustomFoundationModelByIndex(index: Int): Try[Unit] = Try {
    // Create JSON Patch array with remove operation
    val jsonPatch = Json.arr(
      Json.obj(
        "op" -> Json.fromString("remove"),
        "path" -> Json.fromString(s"/spec/custom_foundation_models/$index")
      )
    )

    val patchString = jsonPatch.noSpaces
    println(s"Sending JSON Patch: $patchString")

    // Get the underlying HTTP client
    val apiClient = customObjectsApi.getApiClient
    val httpClient = apiClient.getHttpClient

    // Construct the URL for the custom resource
    val baseUrl = apiClient.getBasePath
    val url = s"$baseUrl/apis/$GROUP/$VERSION/namespaces/$NAMESPACE/$PLURAL/$NAME"

    // Create the request with proper JSON Patch headers
    val requestBody = okhttp3.RequestBody.create(
      okhttp3.MediaType.parse("application/json-patch+json"),
      patchString
    )
    val requestBuilder = new okhttp3.Request.Builder()
      .url(url)
      .patch(requestBody)
      .addHeader("Content-Type", "application/json-patch+json")
      .addHeader("Accept", "application/json")
    val authHeaders = apiClient.getAuthentications
    authHeaders.forEach { case (_, auth) =>
      auth match {
        case bearerAuth: io.kubernetes.client.openapi.auth.HttpBearerAuth =>
          val token = bearerAuth.getBearerToken
          if (token != null && token.nonEmpty) {
            requestBuilder.addHeader("Authorization", s"Bearer $token")
          }
        case apiKeyAuth: io.kubernetes.client.openapi.auth.ApiKeyAuth =>
          val apiKey = apiKeyAuth.getApiKey
          val keyPrefix = apiKeyAuth.getApiKeyPrefix
          if (apiKey != null && apiKey.nonEmpty) {
            val headerValue = if (keyPrefix != null && keyPrefix.nonEmpty) {
              s"$keyPrefix $apiKey"
            } else {
              apiKey
            }
            requestBuilder.addHeader(apiKeyAuth.getLocation match {
              case "header" => apiKeyAuth.getParamName
              case _ => "Authorization"
            }, headerValue)
          }
        case _ => // Handle other auth types if needed
      }
    }
    
    val request = requestBuilder.build()

    // Add authentication headers if present
    val authenticatedRequest = httpClient.newCall(request)
    
    try {
      // Execute the request
      val response = authenticatedRequest.execute()

      if (!response.isSuccessful) {
        val responseBody = if (response.body() != null) response.body().string() else "No response body"
        throw new RuntimeException(
          s"JSON Patch failed [code=${response.code()}]: $responseBody"
        )
      }

      response.close()
      println(s"✅ Successfully removed model at index $index using JSON Patch")

    } catch {
      case ex: IOException =>
        throw new RuntimeException(s"Network error during JSON Patch operation: ${ex.getMessage}", ex)
      case ex: RuntimeException =>
        throw ex
      case ex: Exception =>
        throw new RuntimeException(s"Unexpected error during JSON Patch operation: ${ex.getMessage}", ex)
    }
  }


  // Add a new custom foundation model using JSON Patch
  def addCustomFoundationModel(newModel: CustomFoundationModel): Try[Unit] = Try {
    // Create JSON Patch array with add operation (appends to end of array)
    val jsonPatch = Json.arr(
      Json.obj(
        "op" -> Json.fromString("add"),
        "path" -> Json.fromString("/spec/custom_foundation_models/-"),
        "value" -> newModel.asJson
      )
    )

    val patchString = jsonPatch.noSpaces
    println(s"Sending JSON Patch: $patchString")

    // Get the underlying HTTP client
    val apiClient = customObjectsApi.getApiClient
    val httpClient = apiClient.getHttpClient

    // Construct the URL for the custom resource
    val baseUrl = apiClient.getBasePath
    val url = s"$baseUrl/apis/$GROUP/$VERSION/namespaces/$NAMESPACE/$PLURAL/$NAME"

    // Create the request with proper JSON Patch headers
    val requestBody = okhttp3.RequestBody.create(
      okhttp3.MediaType.parse("application/json-patch+json"),
      patchString
    )
    val requestBuilder = new okhttp3.Request.Builder()
      .url(url)
      .patch(requestBody)
      .addHeader("Content-Type", "application/json-patch+json")
      .addHeader("Accept", "application/json")
    val authHeaders = apiClient.getAuthentications
    authHeaders.forEach { case (_, auth) =>
      auth match {
        case bearerAuth: io.kubernetes.client.openapi.auth.HttpBearerAuth =>
          val token = bearerAuth.getBearerToken
          if (token != null && token.nonEmpty) {
            requestBuilder.addHeader("Authorization", s"Bearer $token")
          }
        case apiKeyAuth: io.kubernetes.client.openapi.auth.ApiKeyAuth =>
          val apiKey = apiKeyAuth.getApiKey
          val keyPrefix = apiKeyAuth.getApiKeyPrefix
          if (apiKey != null && apiKey.nonEmpty) {
            val headerValue = if (keyPrefix != null && keyPrefix.nonEmpty) {
              s"$keyPrefix $apiKey"
            } else {
              apiKey
            }
            requestBuilder.addHeader(apiKeyAuth.getLocation match {
              case "header" => apiKeyAuth.getParamName
              case _ => "Authorization"
            }, headerValue)
          }
        case _ => // Handle other auth types if needed
      }
    }

    val request = requestBuilder.build()

    // Add authentication headers if present
    val authenticatedRequest = httpClient.newCall(request)

    try {
      // Execute the request
      val response = authenticatedRequest.execute()

      if (!response.isSuccessful) {
        val responseBody = if (response.body() != null) response.body().string() else "No response body"
        throw new RuntimeException(
          s"JSON Patch failed [code=${response.code()}]: $responseBody"
        )
      }

      response.close()
      println(s"✅ Successfully added model ${newModel.model_id} using JSON Patch")

    } catch {
      case ex: IOException =>
        throw new RuntimeException(s"Network error during JSON Patch operation: ${ex.getMessage}", ex)
      case ex: RuntimeException =>
        throw ex
      case ex: Exception =>
        throw new RuntimeException(s"Unexpected error during JSON Patch operation: ${ex.getMessage}", ex)
    }
  }

  // Replace a model at a specific index using JSON Patch
  def replaceCustomFoundationModelByIndex(index: Int, updatedModel: CustomFoundationModel): Try[Unit] = Try {
    val jsonPatch = Json.arr(
      Json.obj(
        "op" -> Json.fromString("replace"),
        "path" -> Json.fromString(s"/spec/custom_foundation_models/$index"),
        "value" -> updatedModel.asJson
      )
    )

    val patchString = jsonPatch.noSpaces
    val patchList = jackson.readValue(patchString, classOf[java.util.List[Object]])

    println(s"Sending JSON Patch: $patchString")

    try {
      customObjectsApi.patchNamespacedCustomObject(
        GROUP,
        VERSION,
        NAMESPACE,
        PLURAL,
        NAME,
        patchList
      ).execute()

      println(s"✅ Successfully replaced model at index $index using JSON Patch")
    } catch {
      case ex: ApiException =>
        throw new RuntimeException(
          s"JSON Patch failed [code=${ex.getCode}]: ${ex.getResponseBody}", ex
        )
    }
  }

  // Update a custom foundation model by ID
  def updateCustomFoundationModel(modelId: String, updatedModel: CustomFoundationModel): Try[Unit] = {
    for {
      index <- findModelIndex(modelId)
      result <- replaceCustomFoundationModelByIndex(index, updatedModel)
    } yield result
  }

  // List all model IDs (convenience method)
  def listModelIds: Try[List[String]] = {
    getCustomFoundationModels.map(_.map(_.model_id))
  }

  // Get specific model by ID
  def getCustomFoundationModel(modelId: String): Try[CustomFoundationModel] = {
    getCustomFoundationModels.flatMap { models =>
      models.find(_.model_id == modelId) match {
        case Some(model) => Success(model)
        case None => Failure(new RuntimeException(s"Model with ID '$modelId' not found"))
      }
    }
  }
}
