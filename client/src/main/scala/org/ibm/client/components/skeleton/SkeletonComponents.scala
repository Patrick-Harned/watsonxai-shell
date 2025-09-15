package org.ibm.client.components.skeleton


import com.raquo.laminar.api.L.*
import org.ibm.client.components.{Component, cds}

object SkeletonComponents extends Component {

  def tableSkeleton(
                     columnCount: Int = 5,
                     rowCount: Int = 5,
                     showHeader: Boolean = true,
                     showToolbar: Boolean = true
                   ): Element = {
    cds"table-skeleton"(
      strattr("column-count") := columnCount.toString,
      strattr("row-count") := rowCount.toString,
      strattr("show-header") := showHeader.toString,
      strattr("show-toolbar") := showToolbar.toString
    )
  }

  def cardSkeleton(): Element = {
    div(
      className := "cds--skeleton",
      div(
        className := "cds--skeleton__placeholder",
        height := "200px",
        width := "100%"
      )
    )
  }

  def listSkeleton(itemCount: Int = 5): Element = {
    div(
      className := "skeleton-list",
      (1 to itemCount).map { _ =>
        div(
          className := "cds--skeleton cds--skeleton__text",
          marginBottom := "1rem",
          width := "100%",
          height := "20px"
        )
      }
    )
  }

  def formSkeleton(): Element = {
    div(
      className := "skeleton-form",
      (1 to 4).map { _ =>
        div(
          className := "cds--form-item",
          marginBottom := "1rem",
          div(
            className := "cds--skeleton cds--skeleton__text",
            width := "150px",
            height := "16px",
            marginBottom := "8px"
          ),
          div(
            className := "cds--skeleton cds--skeleton__text",
            width := "100%",
            height := "40px"
          )
        )
      }
    )
  }
}
