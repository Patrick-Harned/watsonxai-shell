package org.ibm.client.components.datatable

import com.raquo.laminar.api.L.*
import org.ibm.client.components.{Component, cds}
import org.scalajs.dom

import scala.scalajs.js


trait DataTable[T] extends Component {

  /**
   * Abstract method to be implemented by concrete DataTable instances.
   * This provides a unique key for the root `cds-table` element,
   * which helps Laminar and Carbon Web Components manage their lifecycle
   * and prevents state bleeding when switching between different table views.
   */
  protected def getTableKey: String


  def render(
              data: List[T],
              config: TableConfig
            )(using converter: ToTableRow[T]): Element = {

    val selectedRowsVar = Var(Set.empty[String])
    val searchTermVar = Var("")

    // Convert data to table rows
    val tableRows = data.zipWithIndex.map { case (item, index) =>
      converter.toTableRow(item, index.toString)
    }

    // Filter rows based on search
    val filteredRowsSignal = searchTermVar.signal.map { searchTerm =>
      if (searchTerm.isEmpty) {
        tableRows
      } else {
        tableRows.filter { row =>
          row.cells.values.exists(_.toString.toLowerCase.contains(searchTerm.toLowerCase))
        }
      }
    }

    val selectedRowsSignal = selectedRowsVar.signal
    // val hasSelectedRows = selectedRowsSignal.map(_.nonEmpty) // This var is unused, can remove if not used elsewhere

    cds"table"(
      // *** THE CRUCIAL FIX: Add a unique key to the root cds-table element ***
      strattr("key") := getTableKey,

      strattr("locale") := "en",
      strattr("size") := "lg",

      // Title and description
      cds"table-header-title"(
        strattr("slot") := "title",
        config.title
      ),
      config.description.map { desc =>
        cds"table-header-description"(
          strattr("slot") := "description",
          desc
        )
      }.getOrElse(emptyNode),

      // Toolbar
      cds"table-toolbar"(
        strattr("slot") := "toolbar",

        // Batch actions
        if (config.batchActions.nonEmpty) {
          cds"table-batch-actions"(
            // Note: Carbon web components handle the active state automatically
            // based on row selection, so we don't need to manually set ?active
            config.batchActions.map { action =>
              cds"button"(
                strattr("data-context") := "data-table", // Important for Carbon to find its associated table
                onClick --> { _ =>
                  // Query Carbon's internal selection state directly from the *current* cds-table instance
                  // We need to query by ID or ref, but querying the document for the first `cds-table` might be sufficient
                  // if you only have one DataTable rendered at a time in the main content area.
                  // If you ever render multiple DataTables on a single page, this query would need to be more specific.
                  val tableElement = dom.document.querySelector(s"cds-table[key='${getTableKey}']").asInstanceOf[js.Dynamic]

                  val selectedIds = if (tableElement != null && tableElement._selectedRows != null) {
                    val selectedElements = tableElement._selectedRows.asInstanceOf[js.Array[dom.Element]]
                    selectedElements.map { element =>
                      element.getAttribute("selection-name")
                    }.filter(_ != null).toSet
                  } else {
                    Set.empty[String]
                  }

                  println(s"Carbon selected IDs for ${getTableKey}: $selectedIds") // Debug line

                  // Find the corresponding TableRow objects
                  val selectedRows = tableRows.filter(row => selectedIds.contains(row.id))
                  println(s"Found ${selectedRows.size} selected rows for ${getTableKey}") // Debug line

                  action.handler(selectedRows)
                },
                action.label,
                action.icon.getOrElse(emptyNode)
              )
            }
          )
        } else emptyNode,

        // Toolbar content
        cds"table-toolbar-content"(
          // Note: has-batch-actions is also handled automatically by Carbon

          // Search
          if (config.searchable) {
            cds"table-toolbar-search"(
              strattr("placeholder") := "Filter table",
              onInput.mapToValue --> searchTermVar.writer
            )
          } else emptyNode,

          // Add button (optional) - you might want to make this configurable too
          cds"button"(
            "Add New"
          )
        )
      ),

      // Table head - let Carbon handle selection automatically
      cds"table-head"(
        cds"table-header-row"(
          // The selection-name attribute tells Carbon this row supports selection
          // Carbon will automatically add the selection header cell
          if (config.selectable) strattr("selection-name") := "header" else emptyMod,

          // ONLY render the data column headers - no manual selection cell
          converter.columns.map { column =>
            cds"table-header-cell"(
              column.label
            )
          }
        )
      ),

      // Table body
      cds"table-body"(
        children <-- filteredRowsSignal.map { rows =>
          rows.map { row =>
            renderTableRow(row, converter.columns, config.selectable, selectedRowsVar)
          }
        }
      )
    )
  }

  private def renderTableRow(
                              row: TableRow,
                              columns: List[TableColumn],
                              selectable: Boolean,
                              selectedRowsVar: Var[Set[String]] // This var is unused in this method
                            ): Element = {
    cds"table-row"(
      // The selection-name attribute tells Carbon this row can be selected
      // Carbon will automatically add the selection checkbox cell
      if (selectable) strattr("selection-name") := row.id else emptyMod,
      // ONLY render the data cells - no manual selection cell
      columns.map { column =>
        cds"table-cell"(
          formatCellContent(row.cells.get(column.key))
        )
      }
    )
  }

  private def formatCellContent(value: Option[Any]): String = {
    value match {
      case Some(v) => v.toString
      case None => ""
    }
  }
}
