package org.ibm.client.routes

sealed trait Route {
  def path: String
  def title: String
}

object Route {
  case object Dashboard extends Route {
    val path = "/"
    val title = "Dashboard"
  }

  case object PVCs extends Route {
    val path = "/pvcs"
    val title = "Persistent Volume Claims"
  }

  case object StorageClasses extends Route {
    val path = "/storage-classes"
    val title = "Storage Classes"
  }

  case object ModelDownloads extends Route {
    val path = "/modeldownloads"
    val title = "Model Downloads"
  }

  case object CustomFoundationModels extends Route {
    val path = "/customfoundationmodels"
    val title = "Custom Foundation Models"
  }
  case object DownloadedModels extends Route {
    val path = "/downloadedmodels"
    val title = "Downloaded Models"
  }

  // Helper to parse path to route
  def fromPath(path: String): Route = path match {
    case "/" => Dashboard
    case "/pvcs" => PVCs
    case "/storage-classes" => StorageClasses
    case "/modeldownloads" => ModelDownloads             // Correct mapping for ModelDownloads
    case "/customfoundationmodels" => CustomFoundationModels // Correct mapping for CustomFoundationModels
    case "/downloadedmodels" => DownloadedModels // Correct mapping for CustomFoundationModels

    case _ => Dashboard // Default route for any unhandled path
  }

  val allRoutes: List[Route] = List(Dashboard, PVCs, StorageClasses, ModelDownloads, CustomFoundationModels,DownloadedModels) // Ensure all routes are listed
}
