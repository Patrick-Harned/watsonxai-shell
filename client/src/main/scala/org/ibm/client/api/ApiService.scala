package org.ibm.client.api


import sttp.capabilities.WebSockets
import sttp.client3.*

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

trait ApiService {
  protected val backend: SttpBackend[Future, WebSockets] = FetchBackend()

  protected def handleApiResponse[T](
                                      future: Future[Response[Either[ResponseException[String, Exception], T]]],
                                      onSuccess: T => Unit,
                                      onError: String => Unit
                                    ): Unit = {
    future.foreach { response =>
      response.body match {
        case Right(data) => onSuccess(data)
        case Left(error) => onError(error.toString)
      }
    }
  }
}
