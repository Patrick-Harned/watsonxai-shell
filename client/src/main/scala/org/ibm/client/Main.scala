package org.ibm.client

import com.raquo.laminar.api.L.*
import org.ibm.client.components.{UIShell, WatsonxAIIFMTile}
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


    // 3. Mount your Laminar UI with `render` instead of renderOnDom
    render(
      dom.document.getElementById("app"),
      appElement
    )
  }

  private def appElement =
    div(
      Seq(UIShell.render, WatsonxAIIFMTile.render),
    )
}
