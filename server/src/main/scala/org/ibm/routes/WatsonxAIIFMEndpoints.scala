package org.ibm.routes

import cats.effect.IO
import cats.syntax.all.*
import org.http4s.HttpRoutes
import org.ibm.database.Database
import org.ibm.shared.{CustomFoundationModel, DownloadedModel, WatsonxAIIFM}
import org.ibm.watsonxaiifm.Client
import sttp.tapir.*
import sttp.tapir.generic.auto.*
import sttp.tapir.json.circe.*
import sttp.tapir.server.ServerEndpoint
import sttp.tapir.server.http4s.Http4sServerInterpreter

import scala.util.{Failure, Success, Try}

object WatsonxAIIFMEndpoints {


  // 2a. Production: serve from classpath /web
  private val watsonxAIIFMEndpoint: ServerEndpoint[Any, IO] = {
    endpoint.get
      .in("api" / "watsonxaiifm")
      .out(jsonBody[WatsonxAIIFM])
      .serverLogicSuccess(_ => {
        IO {
          val jsonString: Try[WatsonxAIIFM] = Client.getWatsonxAIIFM
          // Let Circe parse it on the server side
          jsonString.toOption match {
            case Some(watsonx) => watsonx
            case None => {
              println(jsonString)
              throw new RuntimeException(s"Parse error")
            }
          }
        }
      })
  }
  private val getDownloadedModels: ServerEndpoint[Any, IO] = {
    endpoint.get
      .in("api" / "downloaded_models")
      .out(jsonBody[List[DownloadedModel]])
      .errorOut(stringBody)
      .serverLogic(request => {
        IO {
          Database.getModels match {
            case Failure(exception) => Left(exception.getMessage)
            case Success(value) => Right(value)
          }

        }
      })
  }
  private val createwatsonxAIIFMEndpoint: ServerEndpoint[Any, IO] = {
    endpoint.post
      .in("api" / "watsonxaiifm")
      .out(jsonBody[WatsonxAIIFM])
      .in(jsonBody[CustomFoundationModel])
      .errorOut(stringBody)
      .serverLogic(request => {
        IO {
          try {
            println(s"Creating  model registration: ${request.model_id} with location class: ${request.location}")
            Client.addCustomFoundationModel(request) match {
              case Success(model) =>
                println(s"Successfully created  model registration: ${model}")
                Client.getWatsonxAIIFM match {
                  case Failure(exception) => Left(s"Failed to update the cr: ${exception.getMessage}")
                  case Success(value) => Right(value)
                }
              case Failure(exception) =>
                Left(s"Failed to create model registration - ${exception.getMessage}")
            }
          } catch {
            case ex: Exception =>
              println(s"Error creating model registration: ${ex.getMessage}")
              ex.printStackTrace()
              Left(s"Failed to create model registration: ${ex.getMessage}")
          }
        }
      })
  }
  private val deletewatsonxAIIFMEndpoint: ServerEndpoint[Any, IO] = {
    endpoint.delete
      .in("api" / "watsonxaiifm" / path[String]("model_id"))
      .out(jsonBody[WatsonxAIIFM])
      .errorOut(stringBody)
      .serverLogic(model_id => {
        IO {
          try {
            println(s"Deleting model registration : ${model_id} with location class")
            Client.removeCustomFoundationModel(model_id) match {
              case Success(model) =>
                println(s"Successfully dweleted model registration : ${model_id}")
                Client.getWatsonxAIIFM match {
                  case Failure(exception) => Left(s"Failed to update the cr: ${exception.getMessage}")
                  case Success(value) => Right(value)
                }
              case Failure(exception) => Left(s"Failed to delete the model registration - ${exception.getMessage}")
            }
          } catch {
            case ex: Exception =>
              println(s"Error registering the model: ${ex.getMessage}")
              ex.printStackTrace()
              Left(s"Failed to register the model : ${ex.getMessage}")
          }
        }
      })
  }
  // FIXED: Create routes properly
  private val createwatsonxAIIFMRoute: HttpRoutes[IO] = Http4sServerInterpreter[IO]().toRoutes(createwatsonxAIIFMEndpoint)
  private val deletewatsonxAIIFMRoute: HttpRoutes[IO] = Http4sServerInterpreter[IO]().toRoutes(deletewatsonxAIIFMEndpoint) // FIXED: Use correct endpoint
  private val watsonxAIIFMRoutes: HttpRoutes[IO] = Http4sServerInterpreter[IO]().toRoutes(watsonxAIIFMEndpoint)
  private val getDownloadedModelsRoutes: HttpRoutes[IO] = Http4sServerInterpreter[IO]().toRoutes(getDownloadedModels)
  // FIXED: Combine WatsonxAIIFM routes properly
  val watsonxAIIFMAllRoutes = createwatsonxAIIFMRoute <+> deletewatsonxAIIFMRoute <+> watsonxAIIFMRoutes <+> getDownloadedModelsRoutes
  // FIXED: Remove duplicate allRoutes definition and include the delete route

}
