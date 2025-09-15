package org.ibm.client.components

import com.raquo.laminar.api.L.*
import org.ibm.client.components.notifications.NotificationManager
import org.ibm.shared.WatsonxAIIFM
import sttp.capabilities.WebSockets
import sttp.client3.*
import sttp.client3.circe.*

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import scala.scalajs.js.timers.*

object WatsonxAIIFMTile extends Component {

  def render = {
    val backend: SttpBackend[Future, WebSockets] = FetchBackend()
    val watsonxVar = Var[Option[WatsonxAIIFM]](None)
    val isInitialLoadingVar = Var(true)
    val isRefreshingVar = Var(false)
    val isExpandedVar = Var(false)
    val errorVar = Var[Option[String]](None)
    val pollIntervalHandle = Var[Option[SetIntervalHandle]](None)

    // Extract progress percentage from progress string (e.g., "75%" -> 75)
    def getProgressPercentage(progressStr: String): Int = {
      progressStr.replace("%", "").trim.toIntOption.getOrElse(0)
    }

    // Check if polling is needed (progress < 100%)
    def shouldPoll(watsonx: WatsonxAIIFM): Boolean = {
      val progress = getProgressPercentage(watsonx.status.progress)
      progress < 100 && watsonx.status.watsonxaiifmStatus != "Failed"
    }

    // Fetch data function
    def fetchData(isRefresh: Boolean = false, showNotifications: Boolean = false): Unit = {
      if (isRefresh) {
        isRefreshingVar.set(true)
      } else if (watsonxVar.now().isEmpty) {
        isInitialLoadingVar.set(true)
      }

      errorVar.set(None)

      basicRequest
        .get(uri"/api/watsonxaiifm")
        .response(asJson[WatsonxAIIFM])
        .send(backend)
        .map(_.body)
        .foreach {
          case Right(watsonx) =>
            val wasEmpty = watsonxVar.now().isEmpty
            watsonxVar.set(Some(watsonx))
            isInitialLoadingVar.set(false)
            isRefreshingVar.set(false)
            errorVar.set(None)

            // Show success notification on initial load or refresh if requested
            if (showNotifications) {
              NotificationManager.showSuccess(
                s"WatsonX AI IFM status updated - ${watsonx.status.watsonxaiifmStatus}",
                "Status Refreshed"
              )
            } else if (wasEmpty) {
              // Silent success on initial load, but log it
              println(s"WatsonX AI IFM loaded successfully - Status: ${watsonx.status.watsonxaiifmStatus}")
            }

            // Set up polling if needed
            setupPolling(watsonx)

          case Left(err) =>
            val errorMessage = s"Failed to load WatsonX AI IFM: $err"
            errorVar.set(Some(errorMessage))
            isInitialLoadingVar.set(false)
            isRefreshingVar.set(false)

            // Show error notification
            NotificationManager.showError(
              errorMessage,
              "Load Failed"
            )

            // Clear polling on error
            pollIntervalHandle.now().foreach(clearInterval)
            pollIntervalHandle.set(None)
        }
    }

    // Setup polling logic
    def setupPolling(watsonx: WatsonxAIIFM): Unit = {
      // Clear existing polling
      pollIntervalHandle.now().foreach(clearInterval)

      if (shouldPoll(watsonx)) {
        println(s"Setting up polling - Progress: ${watsonx.status.progress}")
        val handle = setInterval(10000) { // Poll every 10 seconds
          println("Polling for status update...")
          fetchData(isRefresh = true, showNotifications = false) // Silent polling
        }
        pollIntervalHandle.set(Some(handle))
      } else {
        println(s"No polling needed - Progress: ${watsonx.status.progress}")
        pollIntervalHandle.set(None)
      }
    }

    // Manual refresh function
    def refreshData(): Unit = {
      fetchData(isRefresh = true, showNotifications = true)
    }

    // Initial fetch
    fetchData(isRefresh = false, showNotifications = false)

    // Clean up polling on unmount
    onUnmountCallback { _ =>
      pollIntervalHandle.now().foreach(clearInterval)
    }

    // Main render with notifications
    div(
      // Notifications at the top level
      NotificationManager.render,

      div(
        className := "cds--grid",
        div(
          className := "cds--row",
          div(
            className := "cds--col-lg-8 cds--col-md-6 cds--col-sm-4",

            // Show skeleton during initial loading
            child <-- isInitialLoadingVar.signal.map { isInitialLoading =>
              if (isInitialLoading) {
                renderSkeleton()
              } else {
                // Main tile
                cds"tile"(
                  className := "watsonx-tile",
                  onClick --> { _ => isExpandedVar.update(!_) },

                  // Error state
                  child <-- errorVar.signal.map {
                    case Some(error) => renderErrorState(error, refreshData)
                    case None => emptyNode
                  },

                  // Main content with refresh indicator
                  child <-- Signal.combine(watsonxVar.signal, errorVar.signal).map {
                    case (Some(watsonx), None) => renderMainContent(watsonx)
                    case _ => emptyNode
                  },

                  // Refresh indicator overlay
                  child <-- isRefreshingVar.signal.map { isRefreshing =>
                    if (isRefreshing) renderRefreshIndicator() else emptyNode
                  }
                )
              }
            }
          )
        ),

        // Expanded details - full width
        child <-- Signal.combine(isExpandedVar.signal, watsonxVar.signal).map {
          case (true, Some(watsonx)) => renderExpandedView(watsonx, refreshData)
          case _ => emptyNode
        }
      )
    )
  }

  // Skeleton component for smooth loading
  private def renderSkeleton() = {
    cds"tile"(
      className := "watsonx-tile watsonx-tile-skeleton",

      div(
        className := "tile-main-content",

        // Header skeleton
        div(
          className := "tile-header",
          div(
            className := "cds--skeleton cds--skeleton__heading",
            width := "200px",
            height := "24px",
            marginBottom := "1rem"
          ),
          div(
            className := "tile-badges",
            div(className := "cds--skeleton cds--skeleton__text", width := "80px", height := "20px"),
            div(className := "cds--skeleton cds--skeleton__text", width := "60px", height := "20px")
          )
        ),

        // Progress skeleton
        div(
          className := "tile-progress-section",
          marginBottom := "1rem",
          div(className := "cds--skeleton cds--skeleton__text", width := "150px", height := "16px")
        ),

        // Metrics skeleton
        div(
          className := "tile-metrics",
          (1 to 4).map { _ =>
            div(
              className := "metric-row",
              marginBottom := "0.5rem",
              div(className := "cds--skeleton cds--skeleton__text", width := "120px", height := "16px")
            )
          }
        ),

        // Models section skeleton
        div(
          className := "custom-models-summary",
          marginTop := "1rem",
          padding := "0.75rem",
          backgroundColor := "#f4f4f4",
          borderRadius := "4px",

          div(className := "cds--skeleton cds--skeleton__text", width := "180px", height := "16px", marginBottom := "0.5rem"),
          div(className := "cds--skeleton cds--skeleton__text", width := "200px", height := "14px", marginBottom := "0.25rem"),
          div(className := "cds--skeleton cds--skeleton__text", width := "180px", height := "14px")
        ),

        // Footer skeleton
        div(
          className := "tile-footer",
          marginTop := "1rem",
          div(className := "cds--skeleton cds--skeleton__text", width := "160px", height := "12px")
        )
      )
    )
  }

  // Error state component
  private def renderErrorState(error: String, retryAction: () => Unit) = {
    div(
      className := "tile-error-state",
      textAlign := "center",
      padding := "2rem 1rem",

      div(
        className := "error-icon",
        fontSize := "3rem",
        marginBottom := "1rem",
        "⚠️"
      ),

      div(
        className := "error-message",
        fontWeight := "600",
        marginBottom := "0.5rem",
        color := "#da1e28",
        "Failed to Load WatsonX AI IFM"
      ),

      div(
        className := "error-details",
        fontSize := "0.875rem",
        color := "#6f6f6f",
        marginBottom := "1.5rem",
        error
      ),

      button(
        className := "cds--btn cds--btn--primary cds--btn--sm",
        onClick --> { _ => retryAction() },
        "Retry"
      )
    )
  }

  // Refresh indicator overlay
  private def renderRefreshIndicator() = {
    div(
      className := "refresh-indicator",
      position := "absolute",
      top := "10px",
      right := "10px",
      zIndex := "1000",
      backgroundColor := "rgba(255, 255, 255, 0.9)",
      borderRadius := "4px",
      padding := "0.5rem",

      cds"loading"(
        strattr("active") := "",
        strattr("description") := "Refreshing",
        strattr("assistive-text") := "Refreshing status",
        strattr("type") := "small"
      )
    )
  }

  private def renderMainContent(watsonx: WatsonxAIIFM) = {
    val progressPercentage = getProgressPercentage(watsonx.status.progress)
    val isInProgress = progressPercentage < 100 && watsonx.status.watsonxaiifmStatus != "Failed"

    div(
      className := "tile-main-content",

      // Header with enhanced badges
      div(
        className := "tile-header",
        h3(className := "tile-title", "WatsonX AI IFM"),
        div(
          className := "tile-badges",
          span(
            className := s"status-badge status-${watsonx.status.watsonxaiifmStatus.toLowerCase}",
            watsonx.status.watsonxaiifmStatus
          ),
          span(className := "version-badge", s"v${watsonx.spec.version}"),
          span(className := "size-badge", watsonx.spec.size.toUpperCase)
        )
      ),

      // Progress section with loading icon
      div(
        className := "tile-progress-section",
        div(className := "metric-row",
          span(className := "metric-label", "Progress:"),
          div(
            className := "progress-container",
            display := "flex",
            alignItems := "center",
            gap := "0.5rem",

            span(className := "metric-value", watsonx.status.progress),

            // Show loading icon if in progress
            if (isInProgress) {
              cds"loading"(
                strattr("active") := "",
                strattr("description") := "In Progress",
                strattr("assistive-text") := "Deployment in progress",
                strattr("type") := "small"
              )
            } else emptyNode
          )
        ),

        // Progress message
          div(
            className := "progress-message",
            fontSize := "0.875rem",
            color := "#6f6f6f",
            marginTop := "0.25rem",
            watsonx.status.progressMessage
          )
      ),

      // Enhanced metrics
      div(
        className := "tile-metrics",
        div(className := "metric-row",
          span(className := "metric-label", "Namespace:"),
          span(className := "metric-value", watsonx.metadata.namespace)
        ),
        div(className := "metric-row",
          span(className := "metric-label", "Custom Models:"),
          span(
            className := "metric-value metric-highlight",
            s"${watsonx.spec.custom_foundation_models.length} registered"
          )
        ),
        div(className := "metric-row",
          span(className := "metric-label", "Storage:"),
          span(className := "metric-value", watsonx.spec.storageClass)
        ),
        div(className := "metric-row",
          span(className := "metric-label", "Build:"),
          span(className := "metric-value", s"#${watsonx.status.watsonxaiifmBuildNumber.getOrElse("Unknown")  }")
        )
      ),

      // Custom foundation models summary
      if (watsonx.spec.custom_foundation_models.nonEmpty) {
        div(
          className := "custom-models-summary",
          marginTop := "1rem",
          padding := "0.75rem",
          backgroundColor := "#f4f4f4",
          borderRadius := "4px",

          div(
            className := "models-header",
            fontWeight := "600",
            marginBottom := "0.5rem",
            "Custom Foundation Models:"
          ),

          div(
            className := "models-list",
            watsonx.spec.custom_foundation_models.take(3).map { model =>
              div(
                className := "model-item",
                fontSize := "0.875rem",
                marginBottom := "0.25rem",
                s"• ${model.model_id}"
              )
            },

            // Show "and X more" if there are more than 3 models
            if (watsonx.spec.custom_foundation_models.length > 3) {
              div(
                className := "more-models",
                fontSize := "0.875rem",
                fontStyle := "italic",
                color := "#6f6f6f",
                s"... and ${watsonx.spec.custom_foundation_models.length - 3} more"
              )
            } else emptyNode
          )
        )
      } else {
        div(
          className := "no-models-message",
          marginTop := "1rem",
          padding := "0.75rem",
          backgroundColor := "#f9f9f9",
          borderRadius := "4px",
          fontSize := "0.875rem",
          color := "#6f6f6f",
          textAlign := "center",
          "No custom foundation models registered"
        )
      },

      // Status indicator and expand hint
      div(
        className := "tile-footer",
        marginTop := "1rem",

        // Last update time
        watsonx.status.conditions.headOption.map { condition =>
          div(
            className := "last-update",
            fontSize := "0.75rem",
            color := "#6f6f6f",
            s"Last updated: ${condition.lastTransitionTime.take(19).replace("T", " ")}"
          )
        }.getOrElse(emptyNode),

        div(
          className := "expand-hint",
          marginTop := "0.5rem",
          "Click to expand details..."
        )
      )
    )
  }
  val isExpandedVar = Var(false)

  private def renderExpandedView(watsonx: WatsonxAIIFM, refreshAction: () => Unit) = {
    div(
      className := "cds--row",
      marginTop := "1rem",
      div(
        className := "cds--col-lg-16",
        cds"tile"(
          className := "watsonx-details-tile",

          // Enhanced header with refresh button
          div(
            className := "details-header",
            display := "flex",
            justifyContent := "space-between",
            alignItems := "center",
            marginBottom := "1.5rem",

            h4("WatsonX AI IFM - Detailed View"),

            div(
              className := "details-actions",
              display := "flex",
              gap := "0.5rem",

              button(
                className := "cds--btn cds--btn--sm cds--btn--tertiary",
                onClick --> { _ => refreshAction() },
                "Refresh"
              ),

              button(
                className := "cds--btn cds--btn--sm cds--btn--ghost",
                onClick --> { _ => isExpandedVar.set(false) },
                "Close Details"
              )
            )
          ),

          // Rest of expanded content remains the same...
          div(
            className := "details-content",

            // Summary section
            div(
              className := "details-section",
              marginBottom := "2rem",

              h5("Summary"),
              div(
                className := "summary-grid",
                display := "grid",
                gap := "1rem",

                div(
                  className := "summary-card",
                  padding := "1rem",
                  border := "1px solid #e0e0e0",
                  borderRadius := "4px",

                  div(fontWeight := "600", "Deployment Info"),
                  div(s"Status: ${watsonx.status.watsonxaiifmStatus}"),
                  div(s"Progress: ${watsonx.status.progress}"),
                  div(s"Size: ${watsonx.spec.size}"),
                  div(s"Version: ${watsonx.spec.version}")
                ),

                div(
                  className := "summary-card",
                  padding := "1rem",
                  border := "1px solid #e0e0e0",
                  borderRadius := "4px",

                  div(fontWeight := "600", "Storage Configuration"),
                  div(s"Storage Class: ${watsonx.spec.storageClass}"),
                  div(s"Block Storage: ${watsonx.spec.blockStorageClass}"),
                  div(s"File Storage: ${watsonx.spec.fileStorageClass}")
                ),

                div(
                  className := "summary-card",
                  padding := "1rem",
                  border := "1px solid #e0e0e0",
                  borderRadius := "4px",

                  div(fontWeight := "600", "Custom Models"),
                  div(s"Total Models: ${watsonx.spec.custom_foundation_models.length}"),
                  div(s"Install List: ${watsonx.spec.install_model_list.length} items"),
                  div(s"Build Number: ${watsonx.status.watsonxaiifmBuildNumber.getOrElse("Unknown") }")
                )
              )
            ),

            // Full tree view
            div(
              className := "tree-section",
              h5("Complete Configuration"),
              div(
                className := "tree-container",
                TreeView.render(watsonx.toTreeNode("WatsonX AI IFM"))
              )
            )
          )
        )
      )
    )
  }

  // Helper method to get progress percentage
  private def getProgressPercentage(progressStr: String): Int = {
    progressStr.replace("%", "").trim.toIntOption.getOrElse(0)
  }
}
