package org.ibm.client

import com.raquo.laminar.api.L.*
import org.ibm.client.components.downloadedmodels.DownloadedModelDataTable
import org.ibm.client.components.foundationmodels.FoundationModelDataTable
import org.ibm.client.components.hardwarespecs.HardwareSpecDataTable
import org.ibm.client.components.modeldownloads.ModelDownloaderDataTable
import org.ibm.client.components.pvcs.PVCDataTable
import org.ibm.client.components.{UIShell, WatsonxAIIFMTile}
import org.ibm.client.routes.Route.HardwareSpecifications
import org.ibm.client.routes.{Route, Router}
import org.scalajs.dom

object Main {

  def main(args: Array[String]): Unit = {
    // Setup browser history listener
    Router.setupHistoryListener()

    // Mount the app
    render(
      dom.document.getElementById("app"),
      appElement
    )
  }

  private def appElement = {
    div(
      UIShell.render,

      // Main content area that changes based on route
      div(
        className := "cds--content",
        child <-- Router.currentRouteSignal.map(renderPage)
      )
    )
  }

  private def renderPage(route: Route): Element = {
    route match {
      case Route.Dashboard =>
        div(
          h1("WatsonX AI Dashboard"),
          WatsonxAIIFMTile.render
        )
      case Route.ModelDownloads =>
        ModelDownloaderDataTable.render
      case Route.PVCs =>
        PVCDataTable.render
      case Route.CustomFoundationModels => FoundationModelDataTable.render
      case Route.DownloadedModels => DownloadedModelDataTable.render
      case Route.HardwareSpecifications => HardwareSpecDataTable.render
    }
  }
}
