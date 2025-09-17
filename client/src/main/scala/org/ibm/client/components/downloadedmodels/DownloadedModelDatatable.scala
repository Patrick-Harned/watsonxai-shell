package org.ibm.client.components.downloadedmodels

import com.raquo.laminar.api.L.*
import org.ibm.client.components.cds
import org.ibm.client.components.datatable.{BatchAction, DataTable, TableConfig, TableRow}
import org.ibm.client.components.pvcs.PVCDataTable.deleteIcon
import org.ibm.client.components.reactive.ReactiveComponent
import org.ibm.client.components.skeleton.SkeletonComponents
import org.ibm.shared.{ConsoleInfo, DownloadedModel}
import sttp.capabilities.WebSockets

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

case class DownloadedModelData(data: List[DownloadedModel], consoleUrl: String)



object DownloadedModelDataTable extends ReactiveComponent[DownloadedModelData] with DataTable[DownloadedModel] {

  // Case class to hold both pods and console URL




  val deleteDownloadedModelModal = new DeleteDownloadedModelModal(
    onSuccess = () => refresh(showNotification = false)
  )

  // Fetch both pods and console URL - NO MORE PLACEHOLDERS
  protected def fetchData(): Future[DownloadedModelData] = {
    import sttp.client3.*
    import sttp.client3.circe.*
    val backend: SttpBackend[Future, WebSockets] = FetchBackend()

    for {
      // Fetch model downloaders
      pods <- basicRequest
        .get(uri"/api/downloaded_models")
        .response(asJson[List[DownloadedModel]])
        .send(backend)
        .map(_.body match {
          case Right(p) => p
          case Left(error) => throw new Exception(s"Failed to fetch model downloaders: $error")
        })

      // Fetch console URL
      consoleUrl <- basicRequest
        .get(uri"/api/watsonxai/console-url")
        .response(asJson[ConsoleInfo])
        .send(backend)
        .map(_.body match {
          case Right(info) => info.url
          case Left(error) => throw new Exception(s"Failed to fetch console URL: $error")
        })
    } yield DownloadedModelData(pods, consoleUrl)
  }

  protected def renderSkeleton(): Element = {
    SkeletonComponents.tableSkeleton(
      columnCount = 6,
      rowCount = 5,
      showHeader = true,
      showToolbar = true
    )
  }

  def batchDeleteDownloadedModels(selectedRows: List[TableRow]): Unit = {
    val podsToDelete = selectedRows.map(_.data.asInstanceOf[DownloadedModel])
    deleteDownloadedModelModal.open(podsToDelete)
  }

  // Updated to use the actual fetched data
  protected def renderContent(data: DownloadedModelData): Element = {
    val DownloadedModelData(pods, consoleUrl) = data

    val batchActions = List(
      BatchAction(
        id = "delete",
        label = "Delete",
        icon = Some(deleteIcon),
        handler = batchDeleteDownloadedModels
      )
    )

    val tableConfig = TableConfig(
      title = "Downloaded models",
      description = Some("Models that have been downloaded to your cluster"),
      batchActions = batchActions,
      searchable = true,
      selectable = true
    )



    div(
      // Add the modal elements to the DOM
      deleteDownloadedModelModal.element,

      // Action bar
      div(
        className := "action-bar",
        cds"button"(
          strattr("kind") := "secondary",
          disabled <-- isRefreshingSignal,
          onClick --> { _ =>
            println("Refreshing...")
            refresh(showNotification = true)
          },
          child <-- isRefreshingSignal.map { isRefreshing =>
            if (isRefreshing) span("Refreshing...")
            else span(refreshIcon, " Refresh")
          }
        )
      ),

      // Data table
      render(pods, tableConfig)
    )
  }

  protected def renderEmptyState(): Element = {
    div(
      className := "empty-state",
      // Add modal elements here too
      deleteDownloadedModelModal.element,

      h3("No Downloaded Models"),
      p("No downloaded models were found"),
      div(
        className := "empty-state-actions",
        cds"button"(
          strattr("kind") := "secondary",
          onClick --> { _ => refresh() },
          refreshIcon,
          " Refresh"
        )
      )
    )
  }

  protected def getComponentName(): String = "Model Downloaders"

  private def refreshPodStatus(name: String, namespace: String): Unit = {
    performAction(
      action = () => {
        import sttp.client3.*
        import sttp.client3.circe.*
        val backend: SttpBackend[Future, WebSockets] = FetchBackend()

        basicRequest
          .get(uri"/api/watsonxai/downloaded_models")
          .response(asJson[DownloadedModel])
          .send(backend)
          .map(_.body match {
            case Right(pod) => pod
            case Left(error) => throw new Exception(error.toString)
          })
      },
      actionName = s"Refresh status for $name",
      showSuccessNotification = false,
      refreshAfterSuccess = true
    )
  }

  // Initialize on object creation
  initialLoad()

  // Icons
  val refreshIcon = svg.svg(
    svg.preserveAspectRatio := "xMidYMid meet",
    svg.xmlns := "http://www.w3.org/2000/svg",
    svg.fill := "currentColor",
    svg.className := "cds--btn__icon",
    svg.width := "16",
    svg.height := "16",
    svg.viewBox := "0 0 32 32",

    svg.path(
      svg.d := "M18 28A12 12 0 0 1 6 16v-2l5 5 1.4-1.4L7.8 13H10A10 10 0 0 0 20 23v2A12 12 0 0 1 8 13H5.8l4.6 4.6L8.6 19 3 13.4 8.6 7.8 10 9.2 5.8 13H8A12 12 0 0 1 20 1 12 12 0 0 1 32 13v2A12 12 0 0 1 20 27z"
    )
  )

  val downloadIcon = svg.svg(
    svg.preserveAspectRatio := "xMidYMid meet",
    svg.xmlns := "http://www.w3.org/2000/svg",
    svg.fill := "currentColor",
    svg.className := "cds--btn__icon",
    svg.width := "16",
    svg.height := "16",
    svg.viewBox := "0 0 32 32",

    svg.path(
      svg.d := "M26 24v4H6v-4H4v4a2 2 0 002 2h20a2 2 0 002-2v-4zM26 14l-1.41-1.41L17 20.17V2h-2v18.17l-7.59-7.58L6 14l10 10 10-10z"
    )
  )

  /**
   * Abstract method to be implemented by concrete DataTable instances.
   * This provides a unique key for the root `cds-table` element,
   * which helps Laminar and Carbon Web Components manage their lifecycle
   * and prevents state bleeding when switching between different table views.
   */
  override protected def getTableKey: String = "downloaded-model"
}
