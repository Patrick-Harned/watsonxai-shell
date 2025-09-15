package org.ibm.client.components.datatable



import com.raquo.laminar.api.L.*

case class TableColumn(
                        key: String,
                        label: String,
                        sortable: Boolean = true,
                        width: Option[String] = None
                      )

case class TableRow(
                     id: String,
                     cells: Map[String, Any],
                     data: Any // Original object for actions
                   )

case class BatchAction(
                        id: String,
                        label: String,
                        icon: Option[Element] = None,
                        handler: List[TableRow] => Unit
                      )

case class TableConfig(
                        title: String,
                        description: Option[String] = None,
                        batchActions: List[BatchAction] = List.empty,
                        searchable: Boolean = true,
                        selectable: Boolean = true
                      )
