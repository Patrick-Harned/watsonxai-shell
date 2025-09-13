package org.ibm.client

import com.raquo.laminar.api.L.*
import org.ibm.shared.HelloResponse
import sttp.client3.*
import sttp.client3.circe.*
import sttp.client3.FetchBackend
import org.scalajs.dom
import sttp.capabilities.WebSockets

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

object Main {

  def main(args: Array[String]): Unit = {
    val greetingVar = Var("Loading…")

    // 1. Create your backend once
    val backend: SttpBackend[Future, WebSockets] = FetchBackend()

    // 2. Fire off the request; .send returns a Future
    basicRequest
      .get(uri"/api/hello")
      .response(asJson[HelloResponse])
      .send(backend)        // Future[Response[Either[...]]]
      .map(_.body)          // Future[Either[Error, HelloResponse]]
      .foreach {
        case Right(HelloResponse(msg)) => greetingVar.set(s"$msg from your new app reloaded!")
        case Left(err)                 => greetingVar.set(s"Error: $err")
      }

    // 3. Mount your Laminar UI with `render` instead of renderOnDom
    render(
      dom.document.getElementById("app"),
      appElement(greetingVar)
    )
  }

  private def appElement(greetingVar: Var[String]) =
    div(
      h1(child.text <-- greetingVar.signal)
    )
}
