package org.ibm.routes



import cats.effect.IO
import io.circe.generic.auto.*
import org.http4s.HttpRoutes
import org.ibm.shared.{CreatePVCRequest, PVC, StorageClass}
import org.ibm.watsonxaiifm.PVCClient
import sttp.tapir.*
import sttp.tapir.generic.auto.*
import sttp.tapir.json.circe.*
import sttp.tapir.server.ServerEndpoint
import sttp.tapir.server.http4s.Http4sServerInterpreter

object PVCEndpoints {

  // GET /api/watsonxai/pvcs - List PVCs with watsonxai/pvc=true
  val getPVCsEndpoint: ServerEndpoint[Any, IO] =
    endpoint.get
      .in("api" / "watsonxai" / "pvcs")
      .out(jsonBody[List[PVC]])
      .errorOut(stringBody)
      .serverLogic { _ =>
        IO {
          try {
            val pvcs = PVCClient.getWatsonxPVCs
            println(s"Found ${pvcs.length} WatsonX PVCs")
            Right(pvcs)
          } catch {
            case ex: Exception =>
              println(s"Error getting PVCs: ${ex.getMessage}")
              ex.printStackTrace()
              Left(s"Failed to get PVCs: ${ex.getMessage}")
          }
        }
      }

  // POST /api/watsonxai/pvcs - Create PVC with watsonxai/pvc=true
  val createPVCEndpoint: ServerEndpoint[Any, IO] =
    endpoint.post
      .in("api" / "watsonxai" / "pvcs")
      .in(jsonBody[CreatePVCRequest])
      .out(jsonBody[PVC])
      .errorOut(stringBody)
      .serverLogic { request =>
        IO {
          try {
            println(s"Creating PVC: ${request.name} with storage class: ${request.storageClassName}")
            PVCClient.createWatsonxPVC(request) match {
              case Some(pvc) =>
                println(s"Successfully created PVC: ${pvc.metadata.name}")
                Right(pvc)
              case None =>
                Left("Failed to create PVC - unknown error")
            }
          } catch {
            case ex: Exception =>
              println(s"Error creating PVC: ${ex.getMessage}")
              ex.printStackTrace()
              Left(s"Failed to create PVC: ${ex.getMessage}")
          }
        }
      }

  // DELETE /api/watsonxai/pvcs/{name} - Delete PVC by name
  val deletePVCEndpoint: ServerEndpoint[Any, IO] =
    endpoint.delete
      .in("api" / "watsonxai" / "pvcs" / path[String]("name"))
      .out(stringBody)
      .errorOut(stringBody)
      .serverLogic { name =>
        IO {
          try {
            println(s"Deleting PVC: $name")
            if (PVCClient.deleteWatsonxPVC(name)) {
              Right(s"Successfully deleted PVC: $name")
            } else {
              Left(s"Failed to delete PVC: $name - PVC not found or does not have required label")
            }
          } catch {
            case ex: Exception =>
              println(s"Error deleting PVC $name: ${ex.getMessage}")
              ex.printStackTrace()
              Left(s"Failed to delete PVC $name: ${ex.getMessage}")
          }
        }
      }

  // GET /api/watsonxai/storageclasses - List all storage classes
  val getStorageClassesEndpoint: ServerEndpoint[Any, IO] =
    endpoint.get
      .in("api" / "watsonxai" / "storageclasses")
      .out(jsonBody[List[StorageClass]])
      .errorOut(stringBody)
      .serverLogic { _ =>
        IO {
          try {
            val storageClasses = PVCClient.getStorageClasses
            println(s"Found ${storageClasses.length} storage classes")
            Right(storageClasses)
          } catch {
            case ex: Exception =>
              println(s"Error getting storage classes: ${ex.getMessage}")
              ex.printStackTrace()
              Left(s"Failed to get storage classes: ${ex.getMessage}")
          }
        }
      }

  // Combine all endpoints
  val allWatsonxAIEndpoints: HttpRoutes[IO] = Http4sServerInterpreter[IO]().toRoutes(List(
    getPVCsEndpoint,
    createPVCEndpoint,
    deletePVCEndpoint,
    getStorageClassesEndpoint
  ))
}
