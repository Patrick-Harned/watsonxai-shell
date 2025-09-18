// org/ibm/routes/HardwareSpecEndpoints.scala
package org.ibm.routes

import cats.effect.IO
import io.circe.generic.auto.*
import org.http4s.HttpRoutes
import org.ibm.shared.*
import org.ibm.watsonxaiifm.HardwareSpecClient
import sttp.tapir.*
import sttp.tapir.generic.auto.*
import sttp.tapir.json.circe.*
import sttp.tapir.server.ServerEndpoint
import sttp.tapir.server.http4s.Http4sServerInterpreter

import scala.concurrent.ExecutionContext.Implicits.global
import scala.util.{Failure, Success}

object HardwareSpecEndpoints {

  // --- Endpoints for Hardware Specifications ---

  // POST /api/watsonxai/hardware_specs - Create a new hardware specification
  private val createHardwareSpecEndpoint: ServerEndpoint[Any, IO] =
    endpoint.post
      .in("api" / "watsonxai" / "hardware_specs")
      .in(jsonBody[HardwareSpecificationCreate])
      .in(query[String]("space_id").description("The ID of the deployment space")) // space_id is required for create
      .out(jsonBody[HardwareSpecificationResource])
      .errorOut(stringBody)
      .serverLogic { case (request, spaceId) =>
        IO.fromFuture(IO(HardwareSpecClient.createHardwareSpec(request, spaceId))).map {
          case Success(resource) =>
            println(s"Successfully created hardware spec: ${resource.metadata.name} in space $spaceId")
            Right(resource)
          case Failure(ex) =>
            System.err.println(s"Error creating hardware spec: ${ex.getMessage}")
            ex.printStackTrace()
            Left(s"Failed to create hardware spec: ${ex.getMessage}")
        }
      }

  // GET /api/watsonxai/hardware_specs - List all hardware specifications (optionally by space_id)
  private val listHardwareSpecsEndpoint: ServerEndpoint[Any, IO] =
    endpoint.get
      .in("api" / "watsonxai" / "hardware_specs")
      .in(query[Option[String]]("space_id").description("Optional: ID of the deployment space to filter by"))
      .out(jsonBody[List[HardwareSpecificationResource]]) // We'll return the list of resources directly
      .errorOut(stringBody)
      .serverLogic { spaceIdOpt =>
        IO.fromFuture(IO(HardwareSpecClient.listHardwareSpecs(spaceIdOpt))).map {
          case Success(listResponse) =>
            println(s"Listed ${listResponse.total_results} hardware specs (space_id: ${spaceIdOpt.getOrElse("all")})")
            Right(listResponse.resources) // Return just the resources list
          case Failure(ex) =>
            System.err.println(s"Error listing hardware specs: ${ex.getMessage}")
            ex.printStackTrace()
            Left(s"Failed to list hardware specs: ${ex.getMessage}")
        }
      }

  // DELETE /api/watsonxai/hardware_specs/{id} - Delete a hardware specification
  private val deleteHardwareSpecEndpoint: ServerEndpoint[Any, IO] =
    endpoint.delete
      .in("api" / "watsonxai" / "hardware_specs" / path[String]("id"))
      .in(query[String]("space_id").description("The ID of the deployment space")) // space_id is required for delete
      .out(stringBody) // Indicate success
      .errorOut(stringBody)
      .serverLogic { case (id, spaceId) =>
        IO.fromFuture(IO(HardwareSpecClient.deleteHardwareSpec(id, spaceId))).map {
          case Success(_) =>
            println(s"Successfully deleted hardware spec: $id from space $spaceId")
            Right(s"Hardware specification $id deleted successfully.")
          case Failure(ex) =>
            System.err.println(s"Error deleting hardware spec $id: ${ex.getMessage}")
            ex.printStackTrace()
            Left(s"Failed to delete hardware spec $id: ${ex.getMessage}")
        }
      }

  // --- Endpoint for Deployment Spaces ---

  // GET /api/watsonxai/deployment_spaces - List all deployment spaces
  private val listDeploymentSpacesEndpoint: ServerEndpoint[Any, IO] =
    endpoint.get
      .in("api" / "watsonxai" / "deployment_spaces")
      .out(jsonBody[List[DeploymentSpaceResource]]) // We'll return the list of resources directly
      .errorOut(stringBody)
      .serverLogic { _ =>
        IO.fromFuture(IO(HardwareSpecClient.listDeploymentSpaces())).map {
          case Success(listResponse) =>
            println(s"Listed ${listResponse.resources.size} deployment spaces.")
            Right(listResponse.resources) // Return just the resources list
          case Failure(ex) =>
            System.err.println(s"Error listing deployment spaces: ${ex.getMessage}")
            ex.printStackTrace()
            Left(s"Failed to list deployment spaces: ${ex.getMessage}")
        }
      }

  // Combine all new endpoints
  val allHardwareSpecEndpoints: HttpRoutes[IO] = Http4sServerInterpreter[IO]().toRoutes(List(
    createHardwareSpecEndpoint,
    listHardwareSpecsEndpoint,
    deleteHardwareSpecEndpoint,
    listDeploymentSpacesEndpoint
  ))
}
