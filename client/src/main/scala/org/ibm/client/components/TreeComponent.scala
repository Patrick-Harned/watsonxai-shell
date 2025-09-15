package org.ibm.client.components

import com.raquo.laminar.api.L.*
import scala.deriving.Mirror
import scala.compiletime.{erasedValue, summonInline, summonFrom}

case class TreeNode(
                     key: String,
                     value: Any,
                     children: List[TreeNode] = List.empty,
                     isExpandable: Boolean = false
                   )

// The typeclass for converting to TreeNode
trait ToTreeNode[A]:
  def toTreeNode(a: A, key: String): TreeNode

object ToTreeNode:
  // Base instances for primitive types
  given ToTreeNode[String] = (s, key) => TreeNode(key, s, List.empty, false)
  given ToTreeNode[Int] = (i, key) => TreeNode(key, i, List.empty, false)
  given ToTreeNode[Double] = (d, key) => TreeNode(key, d, List.empty, false)
  given ToTreeNode[Boolean] = (b, key) => TreeNode(key, b, List.empty, false)
  given ToTreeNode[Long] = (l, key) => TreeNode(key, l, List.empty, false)
  given ToTreeNode[Float] = (f, key) => TreeNode(key, f, List.empty, false)

  // Instance for Option
  given [T](using show: ToTreeNode[T]): ToTreeNode[Option[T]] = (opt, key) =>
    opt match
      case Some(value) =>
        val children = List(show.toTreeNode(value, "value"))
        TreeNode(key, opt, children, true)
      case None =>
        TreeNode(key, "None", List.empty, false)

  // Instance for List
  given [T](using show: ToTreeNode[T]): ToTreeNode[List[T]] = (list, key) =>
    val children = list.zipWithIndex.map { case (item, index) =>
      show.toTreeNode(item, s"[$index]")
    }
    TreeNode(key, list, children, children.nonEmpty)

  // Instance for Map
  given [K, V](using showV: ToTreeNode[V]): ToTreeNode[Map[K, V]] = (map, key) =>
    val children = map.map { case (mapKey, mapValue) =>
      showV.toTreeNode(mapValue, mapKey.toString)
    }.toList
    TreeNode(key, map, children, children.nonEmpty)

  // Generic derivation for products (case classes)
  inline given derived[T](using m: Mirror.Of[T]): ToTreeNode[T] =
    inline m match
      case p: Mirror.ProductOf[T] => productToTreeNode(p)
      case _ => fallbackToTreeNode[T]

  // Implementation for product types (case classes)
  private inline def productToTreeNode[T](p: Mirror.ProductOf[T])(using m: Mirror.Of[T]): ToTreeNode[T] =
    val elemConverters = summonAll[Tuple.Map[m.MirroredElemTypes, ToTreeNode]]
    val elemLabels = getElemLabels[m.MirroredElemLabels]

    new ToTreeNode[T]:
      def toTreeNode(t: T, key: String): TreeNode =
        val elems = t.asInstanceOf[Product].productIterator.toList
        val children = elems.zip(elemConverters.toList).zip(elemLabels).map {
          case ((elem, converter), label) =>
            converter.asInstanceOf[ToTreeNode[Any]].toTreeNode(elem, label)
        }
        TreeNode(key, t, children, children.nonEmpty)

  // Fallback for types that don't have specific instances
  private def fallbackToTreeNode[T]: ToTreeNode[T] = (t, key) =>
    TreeNode(key, t, List.empty, false)

  // Helper to get field names from tuple of labels
  private inline def getElemLabels[T <: Tuple]: List[String] =
    inline erasedValue[T] match
      case _: EmptyTuple => Nil
      case _: (h *: t) =>
        summonInline[ValueOf[h]].value.asInstanceOf[String] :: getElemLabels[t]

  // Helper to summon all ToTreeNode instances for a tuple of types
  private inline def summonAll[T <: Tuple]: T =
    inline erasedValue[T] match
      case _: EmptyTuple => EmptyTuple.asInstanceOf[T]
      case _: (head *: tail) =>
        (summonInline[head] *: summonAll[tail]).asInstanceOf[T]

object TreeView {

  def render(node: TreeNode, level: Int = 0): Element = {
    val isExpandedVar = Var(false)
    val hasChildren = node.children.nonEmpty

    div(
      className := s"tree-node tree-node--level-$level",
      div(
        className := "tree-node__header",
        paddingLeft := s"${level * 20}px",
        onClick --> { _ => if (hasChildren) isExpandedVar.update(!_) },
        cursor := (if (hasChildren) "pointer" else "default"),

        // Expand/collapse icon
        if (hasChildren) {
          span(
            className <-- isExpandedVar.signal.map(expanded =>
              if (expanded) "tree-node__icon tree-node__icon--expanded"
              else "tree-node__icon tree-node__icon--collapsed"
            ),
            "▶"
          )
        } else {
          span(className := "tree-node__icon tree-node__icon--leaf", "•")
        },

        // Key-value pair
        span(className := "tree-node__key", node.key),
        span(className := "tree-node__separator", ": "),
        span(
          className := "tree-node__value",
          formatValue(node.value, hasChildren)
        )
      ),

      // Children (conditionally rendered)
      child <-- isExpandedVar.signal.map { expanded =>
        if (expanded && hasChildren) {
          div(
            className := "tree-node__children",
            node.children.map(child => render(child, level + 1))
          )
        } else {
          emptyNode
        }
      }
    )
  }

  private def formatValue(value: Any, hasChildren: Boolean): String = {
    if (hasChildren) {
      value match {
        case list: List[_] => s"[${list.length} items]"
        case map: Map[_, _] => s"{${map.size} fields}"
        case _ => "{...}"
      }
    } else {
      value match {
        case s: String if s.length > 50 => s"${s.take(47)}..."
        case s: String => s"\"$s\""
        case null => "null"
        case other => other.toString
      }
    }
  }
}

// Extension method - now non-inline!
extension [T](obj: T) {
  def toTreeNode(name: String = "root")(using converter: ToTreeNode[T]): TreeNode =
    converter.toTreeNode(obj, name)
}
