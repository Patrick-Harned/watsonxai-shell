package org.ibm.client.routes


import com.raquo.laminar.api.L.*
import org.scalajs.dom

object Router {

  // Current route state
  val currentRouteVar: Var[Route] = Var(getInitialRoute())
  val currentRouteSignal: Signal[Route] = currentRouteVar.signal

  // Navigate to a route
  def navigateTo(route: Route): Unit = {
    // Update browser URL without page reload
    dom.window.history.pushState(null, "", route.path)
    currentRouteVar.set(route)
  }

  // Get initial route from current URL
  private def getInitialRoute(): Route = {
    val currentPath = dom.window.location.pathname
    Route.fromPath(currentPath)
  }

  // Handle browser back/forward buttons
  def setupHistoryListener(): Unit = {
    dom.window.addEventListener("popstate", { (_: dom.PopStateEvent) =>
      val newRoute = Route.fromPath(dom.window.location.pathname)
      currentRouteVar.set(newRoute)
    })
  }
}
