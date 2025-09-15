package org.ibm.client.components.foundationmodels

import com.raquo.laminar.api.L.*
import org.ibm.client.api.AppDataContext
import org.ibm.client.components.cds
import org.ibm.client.components.datatable.{BatchAction, TableConfig, TableRow, toDataTable}
import org.ibm.client.components.pvcs.PVCDataTable.deleteIcon
import org.ibm.client.components.reactive.ReactiveComponent
import org.ibm.client.components.skeleton.SkeletonComponents
import org.ibm.shared.{ConsoleInfo, CustomFoundationModel, WatsonxAIIFM}
import sttp.capabilities.WebSockets

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

object FoundationModelDataTable extends ReactiveComponent[FoundationModelDataTable.FoundationModelData] {

  // Case class to hold both pods and console URL
  case class FoundationModelData(data: List[CustomFoundationModel], consoleUrl: String)


  // Create modal instances once
  val createFoundationModelModal = CreateFoundationModelModal(
    onSuccess = () => refresh(showNotification = false)
  )

  val deleteFoundationModelModal = new DeleteFoundationModelModal(
    onSuccess = () => refresh(showNotification = false)
  )

  // Fetch both pods and console URL - NO MORE PLACEHOLDERS
  protected def fetchData(): Future[FoundationModelData] = {
    import sttp.client3.*
    import sttp.client3.circe.*
    val backend: SttpBackend[Future, WebSockets] = FetchBackend()

    for {
      // Fetch model downloaders
      rows <- basicRequest
        .get(uri"/api/watsonxaiifm")
        .response(asJson[WatsonxAIIFM])
        .send(backend)
        .map(_.body match {
          case Right(p) => p.spec.custom_foundation_models
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
    } yield FoundationModelData(rows, consoleUrl)
  }

  protected def renderSkeleton(): Element = {
    SkeletonComponents.tableSkeleton(
      columnCount = 6,
      rowCount = 5,
      showHeader = true,
      showToolbar = true
    )
  }

  def batchDeleteFoundationModels(selectedRows: List[TableRow]): Unit = {
    val rowsToDelete = selectedRows.map(_.data.asInstanceOf[CustomFoundationModel])
    deleteFoundationModelModal.open(rowsToDelete)
  }

  // Updated to use the actual fetched data
  protected def renderContent(data: FoundationModelData): Element = {
    val FoundationModelData(rows, consoleUrl) = data

    val batchActions = List(
      BatchAction(
        id = "delete",
        label = "Delete",
        icon = Some(deleteIcon),
        handler = batchDeleteFoundationModels
      ),
      BatchAction(
        id = "refresh-status",
        label = "Refresh Status",
        icon = Some(refreshIcon),
        handler = (selectedRows: List[TableRow]) => {
          selectedRows.foreach { row =>
            val rowData = row.data.asInstanceOf[CustomFoundationModel]
            refreshPodStatus(rowData.model_id, rowData.location.pvc_name)
          }
        }
      )
    )

    val tableConfig = TableConfig(
      title = "Custom Foundation Models",
      description = Some("Custom Foundation Models loaded and registered"),
      batchActions = batchActions,
      searchable = true,
      selectable = true
    )

    // Using the REAL console URL from the API


    div(
      // Add the modal elements to the DOM
      createFoundationModelModal.element,
      deleteFoundationModelModal.element,

      // Action bar
      div(
        className := "action-bar",
        cds"button"(
          onClick --> { _ =>
            println("Opening create modal...")
            createFoundationModelModal.open()
          },
          downloadIcon,
          "Register a Model"
        ),
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
      rows.toDataTable(tableConfig)
    )
  }

  protected def renderEmptyState(): Element = {
    div(
      className := "empty-state",
      // Add modal elements here too
      createFoundationModelModal.element,
      deleteFoundationModelModal.element,

      h3("No model downloaders found"),
      p("No active model download pods were found. Create a new one to get started."),
      div(
        className := "empty-state-actions",
        cds"button"(
          onClick --> { _ => createFoundationModelModal.open() },
          downloadIcon,
          " Download Model"
        ),
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
          .get(uri"/api/watsonxai/FoundationModels/$name/status?namespace=$namespace")
          .response(asJson[CustomFoundationModel])
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
}
