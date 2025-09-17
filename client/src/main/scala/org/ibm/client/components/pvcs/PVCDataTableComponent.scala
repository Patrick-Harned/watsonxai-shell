package org.ibm.client.components.pvcs

import com.raquo.laminar.api.L.*
import com.raquo.laminar.api.L.svg
import org.ibm.client.components.{Component, CreatePVCModal, DeletePVCModal, cds}
import org.ibm.client.components.datatable.{BatchAction, DataTable, TableConfig, TableRow}
import org.ibm.client.components.notifications.NotificationManager
import org.ibm.client.components.skeleton.SkeletonComponents
import org.ibm.client.components.reactive.ReactiveComponent
import org.ibm.shared.{CreatePVCRequest, PVC}
import sttp.capabilities.WebSockets
import sttp.client3.*
import sttp.client3.circe.*

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

case class PVCDataRow(
                       kind: String,
                       name: String,
                       storageClass: String,
                       status: String,
                       capacity: String
                     )
object PVCDataTable extends ReactiveComponent[List[PVC]] with DataTable[PVCDataRow]{



  // Create modal instances once
  val createPVCModal = CreatePVCModal.apply(
    onSuccess = () => refresh(showNotification = false)
  )

  val deletePVCModal = new DeletePVCModal(
    onSuccess = () => refresh(showNotification = false)
  )

  // Implement required methods
  protected def fetchData(): Future[List[PVC]] = {
    val backend: SttpBackend[Future, WebSockets] = FetchBackend()

    basicRequest
      .get(uri"/api/watsonxai/pvcs")
      .response(asJson[List[PVC]])
      .send(backend)
      .map(_.body match {
        case Right(pvcs) => pvcs
        case Left(error) => throw new Exception(error.toString)
      })
  }

  protected def renderSkeleton(): Element = {
    SkeletonComponents.tableSkeleton(
      columnCount = 5,
      rowCount = 5,
      showHeader = true,
      showToolbar = true
    )
  }

  def batchDeletePVCs(selectedRows: List[TableRow]): Unit = {
    val pvcsToDelete = selectedRows.map(_.data.asInstanceOf[PVCDataRow])
    deletePVCModal.open(pvcsToDelete)
  }

  protected def renderContent(pvcs: List[PVC]): Element = {
    val batchActions = List(
      BatchAction(
        id = "delete",
        label = "Delete",
        icon = Some(deleteIcon),
        handler = batchDeletePVCs
      )
    )

    val tableConfig = TableConfig(
      title = "Persistent Volume Claims",
      description = Some("WatsonX AI PVCs with watsonxai/pvc=true label"),
      batchActions = batchActions,
      searchable = true,
      selectable = true
    )

    val dataRows = pvcs.map(pvc =>
      PVCDataRow(
        kind = pvc.kind,
        name = pvc.metadata.name,
        storageClass = pvc.spec.storageClassName,
        status = pvc.status.map(_.phase.getOrElse("Unknown")).getOrElse("Unknown"),
        capacity = pvc.status.flatMap(_.capacity).map(_.mkString(" : ")).getOrElse("Unknown")
      )
    )

    div(
      // Add modal elements to the DOM
      createPVCModal.element,
      deletePVCModal.element,

      // Action bar
      div(
        className := "action-bar",
        cds"button"(
          onClick --> { _ =>
            println("Opening create PVC modal...")
            createPVCModal.open()
          },
          "Create PVC"
        ),
        cds"button"(
          strattr("kind") := "secondary",
          disabled <-- isRefreshingSignal,
          onClick --> { _ =>
            println("Refreshing PVCs...")
            refresh(showNotification = true)
          },
          child <-- isRefreshingSignal.map { isRefreshing =>
            if (isRefreshing) span("Refreshing...")
            else span("Refresh")
          }
        )
      ),

      // Data table
      render(dataRows, tableConfig)
    )
  }

  protected def renderEmptyState(): Element = {
    div(
      className := "empty-state",
      // Add modal elements here too
      createPVCModal.element,
      deletePVCModal.element,

      h3("No PVCs found"),
      p("No PVCs with the watsonxai/pvc=true label were found."),
      div(
        className := "empty-state-actions",
        cds"button"(
          onClick --> { _ => createPVCModal.open() },
          "Create PVC"
        ),
        cds"button"(
          strattr("kind") := "secondary",
          onClick --> { _ => refresh() },
          "Refresh"
        )
      )
    )
  }

  protected def getComponentName(): String = "PVCs"

  // Delete PVC helper method
  private def deletePVC(name: String): Unit = {
    performAction(
      action = () => {
        val backend: SttpBackend[Future, WebSockets] = FetchBackend()
        basicRequest
          .delete(uri"/api/watsonxai/pvcs/$name")
          .response(asString)
          .send(backend)
          .map(_.body match {
            case Right(_) => name
            case Left(error) => throw new Exception(error)
          })
      },
      actionName = s"Delete PVC $name",
      showSuccessNotification = true,
      refreshAfterSuccess = true
    )
  }

  // Initialize on object creation
  initialLoad()

  // Icons
  val deleteIcon = svg.svg(
    svg.preserveAspectRatio := "xMidYMid meet",
    svg.xmlns := "http://www.w3.org/2000/svg",
    svg.fill := "currentColor",
    svg.className := "cds--btn__icon",
    svg.width := "16",
    svg.height := "16",
    svg.viewBox := "0 0 32 32",

    svg.path(
      svg.d := "M12 12H14V24H12zM18 12H20V24H18z"
    ),

    svg.path(
      svg.d := "M4 6V8H6V28a2 2 0 002 2H24a2 2 0 002-2V8h2V6zM8 28V8H24V28zM12 2H20V4H12z"
    )
  )

  val settingsIcon = div(
    svg.svg(
      svg.preserveAspectRatio := "xMidYMid meet",
      svg.xmlns := "http://www.w3.org/2000/svg",
      svg.fill := "currentColor",
      svg.className := "cds--overflow-menu__icon",
      svg.width := "16",
      svg.height := "16",
      svg.viewBox := "0 0 16 16",

      svg.path(
        svg.d := """M13.5,8.4c0-0.1,0-0.3,0-0.4c0-0.1,0-0.3,0-0.4l1-0.8c0.4-0.3,0.4-0.9,0.2-1.3l-1.2-2C13.3,3.2,13,3,12.6,3
                   |c-0.1,0-0.2,0-0.3,0.1l-1.2,0.4c-0.2-0.1-0.4-0.3-0.7-0.4l-0.3-1.3C10.1,1.3,9.7,1,9.2,1H6.8c-0.5,0-0.9,0.3-1,0.8L5.6,3.1
                   |C5.3,3.2,5.1,3.3,4.9,3.4L3.7,3C3.6,3,3.5,3,3.4,3C3,3,2.7,3.2,2.5,3.5l-1.2,2C1.1,5.9,1.2,6.4,1.6,6.8l0.9,0.9c0,0.1,0,0.3,0,0.4
                   |c0,0.1,0,0.3,0,0.4L1.6,9.2c-0.4,0.3-0.5,0.9-0.2,1.3l1.2,2C2.7,12.8,3,13,3.4,13c0.1,0,0.2,0,0.3-0.1l1.2-0.4
                   |c0.2,0.1,0.4,0.3,0.7,0.4l0.3,1.3c0.1,0.5,0.5,0.8,1,0.8h2.4c0.5,0,0.9-0.3,1-0.8l0.3-1.3c0.2-0.1,0.4-0.2,0.7-0.4l1.2,0.4
                   |c0.1,0,0.2,0.1,0.3,0.1c0.4,0,0.7-0.2,0.9-0.5l1.1-2c0.2-0.4,0.2-0.9-0.2-1.3L13.5,8.4z M12.6,12l-1.7-0.6
                   |c-0.4,0.3-0.9,0.6-1.4,0.8L9.2,14H6.8l-0.4-1.8c-0.5-0.2-0.9-0.5-1.4-0.8L3.4,12l-1.2-2l1.4-1.2c-0.1-0.5-0.1-1.1,0-1.6
                   |L2.2,6l1.2-2l1.7,0.6C5.5,4.2,6,4,6.5,3.8L6.8,2h2.4l0.4,1.8c0.5,0.2,0.9,0.5,1.4,0.8L12.6,4l1.2,2l-1.4,1.2
                   |c0.1,0.5,0.1,1.1,0,1.6l1.4,1.2L12.6,12z""".stripMargin
      ),

      svg.path(
        svg.d := "M8,11c-1.7,0-3-1.3-3-3s1.3-3,3-3s3,1.3,3,3C11,9.6,9.7,11,8,11z M8,6C6.9,6,6,6.8,6,7.9C6,7.9,6,8,6,8" +
          "c0,1.1,0.8,2,1.9,2c0,0,0.1,0,0.1,0c1.1,0,2-0.8,2-1.9c0,0,0-0.1,0-0.1C10,6.9,9.2,6,8,6z"
      )
    ),

    span(
      slot := "tooltip-content",
      "Settings"
    )
  )

  /**
   * Abstract method to be implemented by concrete DataTable instances.
   * This provides a unique key for the root `cds-table` element,
   * which helps Laminar and Carbon Web Components manage their lifecycle
   * and prevents state bleeding when switching between different table views.
   */
  override protected def getTableKey: String = "pvc-data-table"
}
