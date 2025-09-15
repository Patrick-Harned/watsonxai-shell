package org.ibm.routes

import cats.effect.IO
import io.circe.generic.auto.*
import org.http4s.HttpRoutes
import org.ibm.shared.{CreateModelDownloaderRequest, ModelDownloaderPod}
import org.ibm.modeldownloader.ModelDownloaderClient
import sttp.tapir.*
import sttp.tapir.generic.auto.*
import sttp.tapir.json.circe.*
import sttp.tapir.server.ServerEndpoint
import sttp.tapir.server.http4s.Http4sServerInterpreter

object ModelDownloaderEndpoints {

  // GET /api/watsonxai/modeldownloaders - List model downloader pods with watsonxai/model=true
  val getModelDownloaderPodsEndpoint: ServerEndpoint[Any, IO] =
    endpoint.get
      .in("api" / "watsonxai" / "modeldownloaders")
      .in(query[Option[String]]("namespace").description("Namespace to filter pods (optional)"))
      .out(jsonBody[List[ModelDownloaderPod]])
      .errorOut(stringBody)
      .serverLogic { namespaceOpt =>
        IO {
          try {
            val pods = namespaceOpt match {
              case Some(namespace) =>
                ModelDownloaderClient.getModelDownloaderPods(namespace) match {
                  case scala.util.Success(pods) => pods
                  case scala.util.Failure(ex) => throw ex
                }
              case None =>
                ModelDownloaderClient.getAllModelDownloaderPods() match {
                  case scala.util.Success(pods) => pods
                  case scala.util.Failure(ex) => throw ex
                }
            }
            println(s"Found ${pods.length} model downloader pods")
            Right(pods)
          } catch {
            case ex: Exception =>
              println(s"Error getting model downloader pods: ${ex.getMessage}")
              ex.printStackTrace()
              Left(s"Failed to get model downloader pods: ${ex.getMessage}")
          }
        }
      }

  // POST /api/watsonxai/modeldownloaders - Create model downloader pod with watsonxai/model=true
  val createModelDownloaderPodEndpoint: ServerEndpoint[Any, IO] =
    endpoint.post
      .in("api" / "watsonxai" / "modeldownloaders")
      .in(jsonBody[CreateModelDownloaderRequest])
      .out(jsonBody[ModelDownloaderPod])
      .errorOut(stringBody)
      .serverLogic { request =>
        IO {
          try {
            println(s"Creating model downloader pod for model: ${request.modelRepo} with PVC: ${request.pvcName}")
            ModelDownloaderClient.createModelDownloaderPod(
              pvcName = request.pvcName,
              modelRepo = request.modelRepo,
              localDirName = request.localDirName,
              namespace = request.namespace
            ) match {
              case scala.util.Success(pod) =>
                println(s"Successfully created model downloader pod: ${pod.name}")
                Right(pod)
              case scala.util.Failure(ex) =>
                throw ex
            }
          } catch {
            case ex: Exception =>
              println(s"Error creating model downloader pod: ${ex.getMessage}")
              ex.printStackTrace()
              Left(s"Failed to create model downloader pod: ${ex.getMessage}")
          }
        }
      }

  // DELETE /api/watsonxai/modeldownloaders/{name} - Delete model downloader pod by name
  val deleteModelDownloaderPodEndpoint: ServerEndpoint[Any, IO] =
    endpoint.delete
      .in("api" / "watsonxai" / "modeldownloaders" / path[String]("name"))
      .in(query[Option[String]]("namespace").description("Namespace of the pod (defaults to 'default')"))
      .out(stringBody)
      .errorOut(stringBody)
      .serverLogic { case (name, namespaceOpt) =>
        IO {
          try {
            val namespace = namespaceOpt.getOrElse("default")
            println(s"Deleting model downloader pod: $name in namespace: $namespace")

            // First check if the pod exists and has the correct label
            ModelDownloaderClient.getPodStatus(name, namespace) match {
              case scala.util.Success(_) =>
                // Pod exists and has correct label, proceed with deletion
                ModelDownloaderClient.deletePod(name, namespace) match {
                  case scala.util.Success(_) =>
                    Right(s"Successfully deleted model downloader pod: $name")
                  case scala.util.Failure(ex) =>
                    throw ex
                }
              case scala.util.Failure(_) =>
                Left(s"Model downloader pod not found: $name in namespace: $namespace or does not have required label")
            }
          } catch {
            case ex: Exception =>
              println(s"Error deleting model downloader pod $name: ${ex.getMessage}")
              ex.printStackTrace()
              Left(s"Failed to delete model downloader pod $name: ${ex.getMessage}")
          }
        }
      }

  // GET /api/watsonxai/modeldownloaders/{name}/status - Get specific pod status
  val getModelDownloaderPodStatusEndpoint: ServerEndpoint[Any, IO] =
    endpoint.get
      .in("api" / "watsonxai" / "modeldownloaders" / path[String]("name") / "status")
      .in(query[Option[String]]("namespace").description("Namespace of the pod (defaults to 'default')"))
      .out(jsonBody[ModelDownloaderPod])
      .errorOut(stringBody)
      .serverLogic { case (name, namespaceOpt) =>
        IO {
          try {
            val namespace = namespaceOpt.getOrElse("default")
            println(s"Getting status for model downloader pod: $name in namespace: $namespace")

            ModelDownloaderClient.getPodStatus(name, namespace) match {
              case scala.util.Success(pod) =>
                println(s"Pod $name status: ${pod.status.map(_.phase).getOrElse("Unknown")}")
                Right(pod)
              case scala.util.Failure(ex) =>
                throw ex
            }
          } catch {
            case ex: Exception =>
              println(s"Error getting status for model downloader pod $name: ${ex.getMessage}")
              ex.printStackTrace()
              Left(s"Failed to get status for model downloader pod $name: ${ex.getMessage}")
          }
        }
      }

  // GET /api/watsonxai/modeldownloaders/status/{phase} - Get pods by status phase
  val getModelDownloaderPodsByStatusEndpoint: ServerEndpoint[Any, IO] =
    endpoint.get
      .in("api" / "watsonxai" / "modeldownloaders" / "status" / path[String]("phase"))
      .in(query[Option[String]]("namespace").description("Namespace to filter pods (optional)"))
      .out(jsonBody[List[ModelDownloaderPod]])
      .errorOut(stringBody)
      .serverLogic { case (phase, namespaceOpt) =>
        IO {
          try {
            val namespace = namespaceOpt.getOrElse("default")
            println(s"Getting model downloader pods with status: $phase in namespace: $namespace")

            ModelDownloaderClient.getModelDownloaderPodsByStatus(phase, namespace) match {
              case scala.util.Success(pods) =>
                println(s"Found ${pods.length} model downloader pods with status: $phase")
                Right(pods)
              case scala.util.Failure(ex) =>
                throw ex
            }
          } catch {
            case ex: Exception =>
              println(s"Error getting model downloader pods by status $phase: ${ex.getMessage}")
              ex.printStackTrace()
              Left(s"Failed to get model downloader pods by status $phase: ${ex.getMessage}")
          }
        }
      }

  // Combine all endpoints
  val allModelDownloaderEndpoints: HttpRoutes[IO] = Http4sServerInterpreter[IO]().toRoutes(List(
    getModelDownloaderPodsEndpoint,
    createModelDownloaderPodEndpoint,
    deleteModelDownloaderPodEndpoint,
    getModelDownloaderPodStatusEndpoint,
    getModelDownloaderPodsByStatusEndpoint
  ))
}
