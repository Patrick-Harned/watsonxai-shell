package org.ibm.client.components

import com.raquo.laminar.api.L.{*, given}
import com.raquo.laminar.api.L.svg.{fill, d, height, path, preserveAspectRatio, svg, viewBox, width, xmlns}
import com.raquo.laminar.codecs.StringAsIsCodec
import org.ibm.client.routes.{Router, Route}
import org.scalajs.dom

object UIShell extends Component {

  def render: HtmlElement = div(
    cds"header"(
      aria.label := "IBM Platform Name",

      cds"header-menu-button"(
        htmlAttr("button-label-active", StringAsIsCodec) := "Close menu",
        htmlAttr("button-label-inactive", StringAsIsCodec) := "Open menu"
      ),

      cds"header-name"(
        href := "javascript:void 0",
        strattr("prefix") := "IBM",
        onClick --> { _ => Router.navigateTo(Route.Dashboard) },
        "WatsonX AI"
      ),

      cds"side-nav"(
        aria.label := "Side navigation",
        strattr("collapse-mode") := "responsive",

        cds"side-nav-items"(

          // Dashboard
          cds"side-nav-link"(
            href := "javascript:void 0",
            className <-- Router.currentRouteSignal.map { route =>
              if (route == Route.Dashboard) "cds--side-nav__link--current" else ""
            },
            onClick --> { _ => Router.navigateTo(Route.Dashboard) },
            dashboardIcon(),
            "Dashboard"
          ),

          // Storage Menu
          cds"side-nav-menu"(
            strattr("title") := "Storage",

            cds"side-nav-menu-item"(
              href := "javascript:void 0",
              className <-- Router.currentRouteSignal.map { route =>
                if (route == Route.PVCs) "cds--side-nav__link--current" else ""
              },
              onClick --> { _ => Router.navigateTo(Route.PVCs) },
              "PVCs"
            ),

          ),
          cds"side-nav-menu"(
            strattr("title") := "Foundation Models",

            cds"side-nav-menu-item"(
              href := "javascript:void 0",
              className <-- Router.currentRouteSignal.map { route =>
                if (route == Route.ModelDownloads) "cds--side-nav__link--current" else ""
              },
              onClick --> { _ => Router.navigateTo(Route.ModelDownloads) },
              "Model Downloads"
            ),
            cds"side-nav-menu-item"(
              href := "javascript:void 0",
              className <-- Router.currentRouteSignal.map { route =>
                if (route == Route.DownloadedModels) "cds--side-nav__link--current" else ""
              },
              onClick --> { _ => Router.navigateTo(Route.DownloadedModels) },
              "Downloaded Models"
            ),
            cds"side-nav-menu-item"(
              href := "javascript:void 0",
              className <-- Router.currentRouteSignal.map { route =>
                if (route == Route.CustomFoundationModels) "cds--side-nav__link--current" else ""
              },
              onClick --> { _ => Router.navigateTo(Route.CustomFoundationModels) },
              "Custom Foundation Models"
            )
          ),

        )
      )
    )
  )

  // Icons for navigation
  private def dashboardIcon() = svg(
    preserveAspectRatio := "xMidYMid meet",
    xmlns := "http://www.w3.org/2000/svg",
    fill := "currentColor",
    width := "16",
    height := "16",
    viewBox := "0 0 16 16",
    path(d := "M4 2H2v12h2V2zM7 2H5v7h2V2zM10 7H8v7h2V7zM13 10h-2v4h2v-4z")
  )

  private def storageIcon() = svg(
    preserveAspectRatio := "xMidYMid meet",
    xmlns := "http://www.w3.org/2000/svg",
    fill := "currentColor",
    width := "16",
    height := "16",
    viewBox := "0 0 16 16",
    path(d := "M8.5 3a5.5 5.5 0 100 11 5.5 5.5 0 000-11zM3 8.5a5.5 5.5 0 1111 0 5.5 5.5 0 01-11 0z"),
    path(d := "M8 5.5a.5.5 0 01.5.5v2a.5.5 0 01-.5.5H6a.5.5 0 010-1h1.5V6a.5.5 0 01.5-.5z")
  )

  private def modelsIcon() = svg(
    preserveAspectRatio := "xMidYMid meet",
    xmlns := "http://www.w3.org/2000/svg",
    fill := "currentColor",
    width := "16",
    height := "16",
    viewBox := "0 0 16 16",
    path(d := "M2 2a2 2 0 00-2 2v8a2 2 0 002 2h12a2 2 0 002-2V4a2 2 0 00-2-2H2zm0 1h12a1 1 0 011 1v1H1V4a1 1 0 011-1zm0 4h12v6a1 1 0 01-1 1H2a1 1 0 01-1-1V7z")
  )
}
