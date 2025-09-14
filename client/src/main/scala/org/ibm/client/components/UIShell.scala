package org.ibm.client.components

import com.raquo.laminar.api.L.{*, given}
import com.raquo.laminar.api.L.svg.{fill, height, path, preserveAspectRatio, svg, viewBox, width, xmlns}
import com.raquo.laminar.codecs.StringAsIsCodec
import org.ibm.client.components.CarbonImports.CarbonUIShell
import org.scalajs.dom

object UIShell extends Component {
  // 1) Extract your menu‐icon path data
  private val menuIconD =
    "M4.1 12.6l-.6.8c.6.5 1.3.9 2.1 1.2l.3-.9C5.3 13.4 4.7 13 4.1 12.6z" +
      "M2.1 9l-1 .2c.1.8.4 1.6.8 2.3L2.8 11C2.4 10.4 2.2 9.7 2.1 9z" +
      "M5.9 2.4L5.6 1.4C4.8 1.7 4.1 2.1 3.5 2.7l.6.8C4.7 3 5.3 2.6 5.9 2.4z" +
      "M2.8 5L1.9 4.5C1.5 5.2 1.3 6 1.1 6.8l1 .2C2.2 6.3 2.5 5.6 2.8 5z"   +
      "M8 1v1c3.3 0 6 2.7 6 6s-2.7 6-6 6v1c3.9 0 7-3.1 7-7S11.9 1 8 1z"

  // 2) Reusable menu icon
  private def menuIcon =
    svg(
      preserveAspectRatio := "xMidYMid meet",
      xmlns                := "http://www.w3.org/2000/svg",
      fill                 := "currentColor",
      //attr("slot")         := "title-icon",               // ← use `attr` here
      width                := "16",
      height               := "16",
      viewBox              := "0 0 16 16",
      //path(attr("d") := menuIconD)
    )

  // 3) The header + side-nav shell
  def render: HtmlElement = div(


    cds"header"(
      aria.label := "IBM Platform Name",

      cds"header-menu-button"(
        htmlAttr("button-label-active", StringAsIsCodec)   := "Close menu",
        htmlAttr("button-label-inactive", StringAsIsCodec) := "Open menu"
      ),

      cds"header-name"(
        href       := "javascript:void 0",
        strattr("prefix") := "IBM",
        "[Platform]"
      ),

      cds"side-nav"(
        aria.label       := "Side navigation",
        strattr("collapse-mode") := "responsive",

        cds"side-nav-items"(

          // Category 1
          cds"side-nav-menu"(
            strattr("title") := "Category title",
            menuIcon,
            cds"side-nav-menu-item"(href := "https://www.carbondesignsystem.com/", "Link"),
            cds"side-nav-menu-item"(href := "https://www.carbondesignsystem.com/", "Link"),
            cds"side-nav-menu-item"(href := "https://www.carbondesignsystem.com/", "Link")
          ),

          // Category 2
          cds"side-nav-menu"(
            strattr("title") := "Category title",
            menuIcon,
            cds"side-nav-menu-item"(href := "https://www.carbondesignsystem.com/", "Link"),
            cds"side-nav-menu-item"(
              strattr("active") := "",
              aria.current      := "page",
              href              := "https://www.carbondesignsystem.com/",
              "Link"
            ),
            cds"side-nav-menu-item"(href := "https://www.carbondesignsystem.com/", "Link")
          ),

          // Category 3
          cds"side-nav-menu"(
            strattr("title") := "Category title",
            menuIcon,
            cds"side-nav-menu-item"(href := "https://www.carbondesignsystem.com/", "Link"),
            cds"side-nav-menu-item"(href := "https://www.carbondesignsystem.com/", "Link"),
            cds"side-nav-menu-item"(href := "https://www.carbondesignsystem.com/", "Link")
          ),

          // Stand‐alone links
          cds"side-nav-link"(
            href     := "javascript:void(0)",
            menuIcon,
            "Link"
          ),

          cds"side-nav-link"(
            href     := "javascript:void(0)",
            menuIcon,
            "Link"
          )
        )
      )
    )
  )

}
