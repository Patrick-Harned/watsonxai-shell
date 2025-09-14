package org.ibm.client.components

import com.raquo.laminar.api.L.*
import org.ibm.shared.WatsonxAIIFM
import sttp.capabilities.WebSockets
import sttp.client3.*
import sttp.client3.circe.*

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

object WatsonxAIIFMTile extends Component {

  def render =
    val backend: SttpBackend[Future, WebSockets] = FetchBackend()
    val watsonxAIFMVar = Var("Loading")

    basicRequest
      .get(uri"/api/watsonxaiifm")
      .response(asJson[WatsonxAIIFM])
      .send(backend) // Future[Response[Either[...]]]
      .map(_.body) // Future[Either[Error, HelloResponse]]
      .foreach {
        case Right(WatsonxAIIFM(msg)) => watsonxAIFMVar.set(s"$msg from your new app reloaded!")
        case Left(err) => watsonxAIFMVar.set(s"Error: $err")
      }
    div(
      className := "main-content",
      cds"tile"(
        div(
          className := "tile-content",
          h3("WatsonX AI IFM Status"),
          div(
            className := "status-row",
            span("Name: "),
            span("watsonxaiifm-cr"),
            h1(child.text <-- watsonxAIFMVar.signal)
          ),
          div(
            className := "status-row",
            span("Status: "),
            span("Loading...")
          )
        )
      )
    )
}