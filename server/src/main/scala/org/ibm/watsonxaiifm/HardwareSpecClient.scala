package org.ibm.watsonxaiifm

// org/ibm/client/api/HardwareSpecClient.scala (Updated Version)

import cats.effect.IO // Unused here if returning Future, but kept from your imports
import io.circe.generic.auto.* // Assuming you've moved to semiauto, this should be replaced
import io.circe.syntax.*
import org.ibm.shared.*
import sttp.capabilities.WebSockets
import sttp.client3.*
import sttp.client3.asynchttpclient.future.AsyncHttpClientFutureBackend // This is your chosen backend
import sttp.client3.circe.asJson // For asJson
import sttp.model.Uri

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import scala.util.{Failure, Success, Try}
import java.time.Instant

// Define the case class for the Authorization token response
// Ensure this is defined or imported from shared
case class TokenResponse(token: String)
object TokenResponse {
  // Assuming implicit encoders/decoders are defined here or imported
  // For the snippet's sake, if auto is still used, it will work.
  // If using semiauto, ensure these are explicitly defined in its companion object.
  // implicit val encoder: Encoder[TokenResponse] = deriveEncoder
  // implicit val decoder: Decoder[TokenResponse] = deriveDecoder
}

object HardwareSpecClient {

  // Configuration for your WatsonX AI API
  private val BASE_URL: String = sys.env.getOrElse("WATSONXAI_BASE_URL", "https://cpd-cpd.apps.cr-att2.pok-lb.techzone.ibm.com")
  private val USERNAME: String = sys.env.getOrElse("WATSONXAI_USERNAME", "kubeadmin") // Using environment variables is safer
  private val PASSWORD: String = sys.env.getOrElse("WATSONXAI_PASSWORD", "") // Make sure this is secured in production
  private val DEFAULT_SPACE_ID: Option[String] = sys.env.get("WATSONXAI_DEFAULT_SPACE_ID") // Make default space optional

  // Backend Initialization
  // This implicit runtime is for Cats-Effect, but you are using Future
  // private implicit val runtime: cats.effect.unsafe.IORuntime = cats.effect.unsafe.IORuntime.global // This is likely unused and can be removed
  private val asyncBackend =  AsyncHttpClientFutureBackend()


  // --- Authentication ---
  private var cachedToken: Option[String] = None
  private var tokenExpiry: Option[Instant] = None

  private val TOKEN_VALIDITY_SECONDS = 3600L // 60 minutes
  private val TOKEN_REFRESH_BUFFER_SECONDS = 300L // Refresh 5 minutes before actual expiry


  /**
   * Asynchronously obtains an authentication token.
   * Caches the token and refreshes it before expiry.
   *
   * @return A `Future[Try[String]]` which will contain the token on success,
   *         or a failure if token acquisition fails.
   */
  private def getAuthToken: Future[Try[String]] = {
    // Check if we have a valid, unexpired token asynchronously
    val now = Instant.now()
    if (cachedToken.isDefined && tokenExpiry.exists(_.isAfter(now.plusSeconds(TOKEN_REFRESH_BUFFER_SECONDS)))) {
      println("HardwareSpecClient: Using cached authentication token.")
      return Future.successful(Success(cachedToken.get))
    }

    println("HardwareSpecClient: Attempting to obtain a new authentication token asynchronously...")
    val authUri = uri"$BASE_URL/icp4d-api/v1/authorize"
    val authBody = Map("username" -> USERNAME, "password" -> PASSWORD).asJson.noSpaces

    basicRequest
      .post(authUri)
      .header("Content-Type", "application/json")
      .body(authBody)
      .response(asJson[TokenResponse])
      .send(asyncBackend)
      .map { response =>
        response.body match {
          case Right(tokenResponse) =>
            val token = tokenResponse.token
            this.synchronized {
              cachedToken = Some(token)
              tokenExpiry = Some(Instant.now().plusSeconds(TOKEN_VALIDITY_SECONDS))
            }
            println("HardwareSpecClient: Authentication token obtained successfully.")
            Success(token)
          case Left(error) =>
            Failure(new RuntimeException(s"HardwareSpecClient: Failed to obtain authentication token: ${error.getMessage}. Raw response: ${response.body}"))
        }
      }.recover {
        case ex: Throwable =>
          Failure(new RuntimeException(s"HardwareSpecClient: Network/client error during token acquisition: ${ex.getMessage}", ex))
      }
  }

  // --- API Methods ---

  /**
   * Creates a new custom hardware specification.
   * Requires a space_id.
   * @param spec The HardwareSpecificationCreate object.
   * @param spaceId The ID of the deployment space to create the spec in.
   * @return A Future containing a Try of HardwareSpecificationResource (the created resource).
   */
  def createHardwareSpec(spec: HardwareSpecificationCreate, spaceId: String): Future[Try[HardwareSpecificationResource]] = {
    getAuthToken.flatMap { // <--- Now pattern match on the Try[String]
      case Success(token) =>
        val createUri = uri"$BASE_URL/v2/hardware_specifications?space_id=$spaceId"
        val requestBody = spec.asJson.noSpaces
        println(s"HardwareSpecClient: Creating hardware spec at: $createUri with body: $requestBody")

        basicRequest
          .post(createUri)
          .header("Authorization", s"Bearer $token")
          .header("Content-Type", "application/json")
          .body(requestBody)
          .response(asJson[HardwareSpecificationResource])
          .send(asyncBackend)
          .map(_.body match {
            case Right(resource) =>
              println(s"HardwareSpecClient: Successfully created hardware spec: ${resource.metadata.name}")
              Success(resource)
            case Left(error) =>
              Failure(new RuntimeException(s"HardwareSpecClient: Failed to create hardware spec: ${error.getMessage}. Raw: ${error.getMessage}"))
          }).recover { case ex: Throwable => Failure(ex) }
      case Failure(ex) => Future.successful(Failure(ex)) // Propagate auth failure
    }
  }

  /**
   * Lists all custom hardware specifications.
   * If spaceId is None, it lists across all accessible spaces.
   * @param spaceId Optional ID of the deployment space to filter by.
   * @return A Future containing a Try of HardwareSpecificationList.
   */
  def listHardwareSpecs(spaceId: Option[String] = None): Future[Try[HardwareSpecificationList]] = {
    getAuthToken.flatMap { // <--- Now pattern match on the Try[String]
      case Success(token) =>
        val listUri = spaceId match {
          case Some(id) => uri"$BASE_URL/v2/hardware_specifications?space_id=$id"
          case None => uri"$BASE_URL/v2/hardware_specifications" // List across all spaces
        }
        println(s"HardwareSpecClient: Listing hardware specs from: $listUri")

        basicRequest
          .get(listUri)
          .header("Authorization", s"Bearer $token")
          .response(asJson[HardwareSpecificationList])
          .send(asyncBackend)
          .map(_.body match {
            case Right(list) =>
              println(s"HardwareSpecClient: Successfully listed ${list.total_results} hardware specs.")
              Success(list)
            case Left(error) =>
              Failure(new RuntimeException(s"HardwareSpecClient: Failed to list hardware specs: ${error.getMessage}. Raw: ${error.getMessage}"))
          }).recover { case ex: Throwable => Failure(ex) }
      case Failure(ex) => Future.successful(Failure(ex)) // Propagate auth failure
    }
  }


  /**
   * Deletes a custom hardware specification by its ID.
   * Requires a space_id.
   * @param hardwareSpecId The asset_id (GUID) of the hardware specification to delete.
   * @param spaceId The ID of the deployment space where the spec resides.
   * @return A Future containing a Try of Unit (success/failure).
   */
  def deleteHardwareSpec(hardwareSpecId: String, spaceId: String): Future[Try[Unit]] = {
    getAuthToken.flatMap { // <--- Now pattern match on the Try[String]
      case Success(token) =>
        val deleteUri = uri"$BASE_URL/v2/hardware_specifications/$hardwareSpecId?space_id=$spaceId"
        println(s"HardwareSpecClient: Deleting hardware spec: $deleteUri")

        basicRequest
          .delete(deleteUri)
          .header("Authorization", s"Bearer $token")
          .send(asyncBackend)
          .map { resp =>
            if (resp.code.isSuccess) {
              println(s"HardwareSpecClient: Successfully deleted hardware spec $hardwareSpecId.")
              Success(())
            } else {
              Failure(new RuntimeException(s"HardwareSpecClient: Failed to delete hardware spec $hardwareSpecId. Status: ${resp.code}. Body: ${resp.body}"))
            }
          }.recover { case ex: Throwable => Failure(ex) }
      case Failure(ex) => Future.successful(Failure(ex)) // Propagate auth failure
    }
  }

  /**
   * Lists all deployment spaces.
   * @return A Future containing a Try of DeploymentSpaceListResponse.
   */
  def listDeploymentSpaces(): Future[Try[DeploymentSpaceListResponse]] = {
    getAuthToken.flatMap { // <--- Now pattern match on the Try[String]
      case Success(token) =>
        val listUri = uri"$BASE_URL/v2/spaces"
        println(s"HardwareSpecClient: Listing deployment spaces from: $listUri")

        basicRequest
          .get(listUri)
          .header("Authorization", s"Bearer $token")
          .response(asJson[DeploymentSpaceListResponse])
          .send(asyncBackend)
          .map(_.body match {
            case Right(list) =>
              println(s"HardwareSpecClient: Successfully listed ${list.resources.size} deployment spaces.")
              Success(list)
            case Left(error) =>
              Failure(new RuntimeException(s"HardwareSpecClient: Failed to list deployment spaces: ${error.getMessage}. Raw: ${error.getMessage}"))
          }).recover { case ex: Throwable => Failure(ex) }
      case Failure(ex) => Future.successful(Failure(ex)) // Propagate auth failure
    }
  }

  // Shutdown hook to cleanly stop the async backend if it manages resources
  sys.addShutdownHook {
    println("HardwareSpecClient: Shutting down client backend (if applicable)...")
    asyncBackend.close() // Close the backend cleanly
  }
}
