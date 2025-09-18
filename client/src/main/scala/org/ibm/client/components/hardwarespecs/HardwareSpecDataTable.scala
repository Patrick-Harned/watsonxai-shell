// org/ibm/client/components/hardwarespecs/HardwareSpecDataTable.scala
package org.ibm.client.components.hardwarespecs

import com.raquo.laminar.api.L.*
import org.ibm.client.components.cds
import org.ibm.client.components.datatable.{BatchAction, DataTable, TableColumn, TableConfig, TableRow, ToTableRow}
import org.ibm.client.components.pvcs.PVCDataTable.deleteIcon // Reuse delete icon
import org.ibm.client.components.reactive.ReactiveComponent
import org.ibm.client.components.skeleton.SkeletonComponents
import org.ibm.client.routes.Router
import org.ibm.shared.{HardwareSpecificationList, HardwareSpecificationResource}
import sttp.capabilities.WebSockets
import sttp.client3.*
import sttp.client3.circe.*

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import java.time.format.DateTimeFormatter

// Data row type for the DataTable
case class HardwareSpecDataRow(
                                id: String,
                                name: String,
                                description: String,
                                cpuUnits: String,
                                memSize: String,
                                gpuNum: String,
                                spaceName: String, // To display the space name
                                createdAt: String,
                                rawResource: HardwareSpecificationResource // Keep raw resource for delete/details
                              )

object HardwareSpecDataTable extends ReactiveComponent[List[HardwareSpecificationResource]] with DataTable[HardwareSpecDataRow] {

  // Modals for Create and Delete operations
  val createHardwareSpecModal = CreateHardwareSpecModal(
    onSuccess = () => refresh(showNotification = false)
  )

  val deleteHardwareSpecModal = new DeleteHardwareSpecModal(
    onSuccess = () => refresh(showNotification = false)
  )

  // Fetch data (Hardware Specifications) from your backend
  override protected def fetchData(): Future[List[HardwareSpecificationResource]] = {
    val backend: SttpBackend[Future, WebSockets] = FetchBackend() // Client-side fetch backend

    basicRequest
      .get(uri"/api/watsonxai/hardware_specs") // Your server endpoint
      .response(asJson[List[HardwareSpecificationResource]])
      .send(backend)
      .map(_.body match {
        case Right(specs) => specs
        case Left(error) => throw new Exception(s"Failed to fetch hardware specifications: $error")
      })
  }

  // Define how to display the skeleton while loading
  override protected def renderSkeleton(): Element = {
    SkeletonComponents.tableSkeleton(
      columnCount = 8, // Adjust based on your columns
      rowCount = 5,
      showHeader = true,
      showToolbar = true
    )
  }

  // Batch delete handler
  def batchDeleteHardwareSpecs(selectedRows: List[TableRow]): Unit = {
    val specsToDelete = selectedRows.map(_.data.asInstanceOf[HardwareSpecDataRow].rawResource)
    deleteHardwareSpecModal.open(specsToDelete)
  }

  // Render the actual content of the table
  override protected def renderContent(specs: List[HardwareSpecificationResource]): Element = {
    val batchActions = List(
      BatchAction(
        id = "delete",
        label = "Delete",
        icon = Some(deleteIcon),
        handler = batchDeleteHardwareSpecs
      )
      // Add other batch actions if needed
    )

    val tableConfig = TableConfig(
      title = "Custom Hardware Specifications",
      description = Some("Configured custom hardware specifications for foundation models."),
      batchActions = batchActions,
      searchable = true,
      selectable = true
    )

    val dataRows = specs.map { spec =>
      val cpuUnits = spec.entity.hardware_specification.nodes.flatMap(x => x.cpu.map(_.units)).getOrElse("N/A")
      val memSize = spec.entity.hardware_specification.nodes.flatMap(x => x.mem.map(_.size)).getOrElse("N/A")
      val gpuNum = spec.entity.hardware_specification.nodes.flatMap(x => x.gpu.map(_.num_gpu.toString)).getOrElse("N/A")
      val spaceName = spec.metadata.space_id // Assuming space_id is sufficient, or you'd need to map to actual name

      HardwareSpecDataRow(
        id = spec.metadata.asset_id,
        name = spec.metadata.name,
        description = spec.metadata.description.getOrElse(""),
        cpuUnits = cpuUnits,
        memSize = memSize,
        gpuNum = gpuNum,
        spaceName = spaceName.getOrElse("Unknown"),
        createdAt = spec.metadata.created_at.toString,
        rawResource = spec
      )
    }

    div(
      // Modals should always be in the DOM, so they can be opened
      createHardwareSpecModal.element,
      deleteHardwareSpecModal.element,

      // Action bar for the table
      div(
        className := "action-bar",
        cds"button"(
          onClick --> { _ =>
            println("Opening create hardware spec modal...")
            createHardwareSpecModal.open()
          },
          " Create Specification"
        ),
        cds"button"(
          strattr("kind") := "secondary",
          disabled <-- isRefreshingSignal,
          onClick --> { _ =>
            println("Refreshing hardware specs...")
            refresh(showNotification = true)
          },
          child <-- isRefreshingSignal.map { isRefreshing =>
            if (isRefreshing) cds"loading"() else emptyNode
          }
        )
      ),

      // Data table rendering
      render(dataRows, tableConfig) // Calls the render method from DataTable trait
    )
  }

  // Render empty state when no data is available
  override protected def renderEmptyState(): Element = {
    div(
      className := "empty-state",
      createHardwareSpecModal.element, // Ensure modals are present in empty state too
      deleteHardwareSpecModal.element,

      h3("No Custom Hardware Specifications Found"),
      p("You have not yet created any custom hardware specifications."),
      div(
        className := "empty-state-actions",
        cds"button"(
          onClick --> { _ => createHardwareSpecModal.open() },
          " Create Specification"
        ),
        cds"button"(
          strattr("kind") := "secondary",
          onClick --> { _ => refresh() },
          " Refresh"
        )
      )
    )
  }

  // Component name for notifications/logging
  override protected def getComponentName(): String = "Custom Hardware Specifications"

  // Implement the unique key for the DataTable trait
  override protected def getTableKey: String = "hardware-spec-data-table"

  // Implicit converter from HardwareSpecDataRow to TableRow
  implicit val hardwareSpecToTableRow: ToTableRow[HardwareSpecDataRow] = new ToTableRow[HardwareSpecDataRow] {
    override val columns: List[TableColumn] = List(
      TableColumn("name", "Name"),
      TableColumn("description", "Description"),
      TableColumn("cpuUnits", "CPU Units"),
      TableColumn("memSize", "Memory Size"),
      TableColumn("gpuNum", "GPU Count"),
      TableColumn("spaceName", "Deployment Space"),
      TableColumn("createdAt", "Created At")
    )
    override def toTableRow(item: HardwareSpecDataRow, id: String): TableRow = TableRow(
      id = item.id, // Use the actual asset_id as the row ID
      data = item,
      cells = Map(
        "name" -> item.name,
        "description" -> item.description,
        "cpuUnits" -> item.cpuUnits,
        "memSize" -> item.memSize,
        "gpuNum" -> item.gpuNum,
        "spaceName" -> item.spaceName,
        "createdAt" -> item.createdAt
      )
    )
  }

  // Initialize data loading on object creation
  initialLoad()

  // Re-use icons or define new ones
  val refreshIcon = cds"button"("Refresh") // Assuming you have a `cds.refreshIcon` or similar, or redefine
}