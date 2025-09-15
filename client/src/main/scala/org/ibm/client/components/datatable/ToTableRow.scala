package org.ibm.client.components.datatable

import scala.compiletime.{erasedValue, summonInline}
import scala.deriving.Mirror

// The typeclass for converting to table rows
trait ToTableRow[A]:
  def columns: List[TableColumn]
  def toTableRow(a: A, id: String): TableRow

object ToTableRow:
  // Base instances for primitive types (for individual fields)
  given ToTableRow[String] = new ToTableRow[String] {
    def columns = List(TableColumn("value", "Value"))
    def toTableRow(s: String, id: String) = TableRow(id, Map("value" -> s), s)
  }

  given ToTableRow[Int] = new ToTableRow[Int] {
    def columns = List(TableColumn("value", "Value"))
    def toTableRow(i: Int, id: String) = TableRow(id, Map("value" -> i), i)
  }

  given ToTableRow[Double] = new ToTableRow[Double] {
    def columns = List(TableColumn("value", "Value"))
    def toTableRow(d: Double, id: String) = TableRow(id, Map("value" -> d), d)
  }

  given ToTableRow[Boolean] = new ToTableRow[Boolean] {
    def columns = List(TableColumn("value", "Value"))
    def toTableRow(b: Boolean, id: String) = TableRow(id, Map("value" -> b), b)
  }

  // Generic derivation for case classes
  inline given derived[T](using m: Mirror.Of[T]): ToTableRow[T] =
    inline m match
      case p: Mirror.ProductOf[T] => productToTableRow(p)
      case _ => fallbackToTableRow[T]

  // Implementation for product types (case classes)
  private inline def productToTableRow[T](p: Mirror.ProductOf[T])(using m: Mirror.Of[T]): ToTableRow[T] =
    val elemLabels = getElemLabels[m.MirroredElemLabels]

    new ToTableRow[T]:
      def columns: List[TableColumn] =
        elemLabels.map(label => TableColumn(
          key = label,
          label = formatLabel(label),
          sortable = true
        ))

      def toTableRow(t: T, id: String): TableRow =
        val elems = t.asInstanceOf[Product].productIterator.toList
        val cells = elemLabels.zip(elems).map { case (label, value) =>
          label -> formatCellValue(value)
        }.toMap

        TableRow(id, cells, t)

  // Fallback for non-case classes
  private def fallbackToTableRow[T]: ToTableRow[T] = new ToTableRow[T] {
    def columns = List(TableColumn("toString", "Value"))
    def toTableRow(t: T, id: String) = TableRow(id, Map("toString" -> t.toString), t)
  }

  // Helper to get field names from tuple of labels
  private inline def getElemLabels[T <: Tuple]: List[String] =
    inline erasedValue[T] match
      case _: EmptyTuple => Nil
      case _: (h *: t) =>
        summonInline[ValueOf[h]].value.asInstanceOf[String] :: getElemLabels[t]

  // Format field names for display
  private def formatLabel(fieldName: String): String = {
    fieldName
      .replaceAll("([a-z])([A-Z])", "$1 $2") // camelCase to words
      .split("_").map(_.capitalize).mkString(" ") // snake_case to words
  }

  // Format cell values for display
  private def formatCellValue(value: Any): Any = value match {
    case Some(v) => v
    case None => ""
    case list: List[_] => s"${list.length} items"
    case other => other
  }
