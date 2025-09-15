package org.ibm.client.components.reactive

import com.raquo.laminar.api.L.*
import org.ibm.client.components.{Component, cds}
import org.ibm.client.components.notifications.NotificationManager

import scala.concurrent.Future
import scala.concurrent.ExecutionContext.Implicits.global
import scala.util.{Failure, Success}

trait ReactiveComponent[T] extends Component {

  // Core reactive state
  protected val dataVar = Var[Option[T]](None)
  protected val isInitialLoadingVar = Var(true)
  protected val isRefreshingVar = Var(false)
  protected val errorVar = Var[Option[String]](None)

  // Derived signals
  val dataSignal: Signal[Option[T]] = dataVar.signal
  val isInitialLoadingSignal: Signal[Boolean] = isInitialLoadingVar.signal
  val isRefreshingSignal: Signal[Boolean] = isRefreshingVar.signal
  val isLoadingSignal: Signal[Boolean] = Signal.combine(isInitialLoadingVar, isRefreshingVar).map(_ || _)
  val errorSignal: Signal[Option[String]] = errorVar.signal
  val hasDataSignal: Signal[Boolean] = dataSignal.map(_.isDefined)

  // Abstract methods to implement
  protected def fetchData(): Future[T]
  protected def renderSkeleton(): Element
  protected def renderContent(data: T): Element
  protected def renderEmptyState(): Element
  protected def getComponentName(): String

  // Generic fetch handler
  protected def performFetch(
                              isRefresh: Boolean = false,
                              showSuccessNotification: Boolean = false,
                              successMessage: Option[String] = None,
                              onSuccess: Option[T => Unit] = None,
                              onError: Option[String => Unit] = None
                            ): Unit = {

    if (isRefresh) isRefreshingVar.set(true) else isInitialLoadingVar.set(true)
    errorVar.set(None)

    fetchData().onComplete {
      case Success(data) =>
        dataVar.set(Some(data))
        isInitialLoadingVar.set(false)
        isRefreshingVar.set(false)
        errorVar.set(None)

        if (showSuccessNotification) {
          val message = successMessage.getOrElse(s"${getComponentName()} loaded successfully")
          NotificationManager.showSuccess(message, "Success")
        }

        onSuccess.foreach(_(data))

      case Failure(exception) =>
        val errorMessage = s"Failed to load ${getComponentName().toLowerCase}: ${exception.getMessage}"
        errorVar.set(Some(errorMessage))
        isInitialLoadingVar.set(false)
        isRefreshingVar.set(false)

        NotificationManager.showError(errorMessage, "Load Failed")
        onError.foreach(_(errorMessage))
    }
  }

  // Convenience methods
  def initialLoad(): Unit = performFetch(isRefresh = false)

  def refresh(showNotification: Boolean = false): Unit =
    performFetch(isRefresh = true, showSuccessNotification = showNotification)

  def reload(): Unit = {
    dataVar.set(None)
    initialLoad()
  }

  // Generic action handler for buttons/interactive elements
  protected def performAction[R](
                                  action: () => Future[R],
                                  actionName: String,
                                  showSuccessNotification: Boolean = true,
                                  refreshAfterSuccess: Boolean = true,
                                  onSuccess: Option[R => Unit] = None,
                                  onError: Option[String => Unit] = None
                                ): Unit = {

    val actionLoadingVar = Var(false)
    actionLoadingVar.set(true)

    action().onComplete {
      case Success(result) =>
        actionLoadingVar.set(false)

        if (showSuccessNotification) {
          NotificationManager.showSuccess(s"$actionName completed successfully", "Success")
        }

        if (refreshAfterSuccess) {
          refresh()
        }

        onSuccess.foreach(_(result))

      case Failure(exception) =>
        actionLoadingVar.set(false)
        val errorMessage = s"$actionName failed: ${exception.getMessage}"

        NotificationManager.showError(errorMessage, "Action Failed")
        onError.foreach(_(errorMessage))
    }
  }

  // Main render method - FIXED VERSION
  def render: Element = {
    div(
      // Loading skeleton during initial load
      child <-- Signal.combine(isInitialLoadingSignal, hasDataSignal).map {
        case (true, false) => renderSkeleton()
        case _ => emptyNode
      },

      // Error state - FIXED: Use Signal.combine instead of accessing .now()
      child <-- Signal.combine(errorSignal, isLoadingSignal).map {
        case (Some(error), false) => renderErrorState(error)
        case _ => emptyNode
      },

      // Content or empty state
      child <-- Signal.combine(dataSignal, isInitialLoadingSignal, errorSignal).map {
        case (Some(data), false, None) => renderContent(data)
        case (None, false, None) => renderEmptyState()
        case _ => emptyNode
      },

      // Refresh indicator
      child <-- isRefreshingSignal.map { isRefreshing =>
        if (isRefreshing) renderRefreshIndicator() else emptyNode
      }
    )
  }

  // Default error state renderer
  protected def renderErrorState(error: String): Element = {
    cds"inline-notification"(
      strattr("kind") := "error",
      strattr("title") := s"${getComponentName()} Error",
      strattr("subtitle") := error,
      marginBottom := "1rem",
      div(
        className := "error-actions",
        cds"button"(
          strattr("kind") := "tertiary",
          strattr("size") := "sm",
          onClick --> { _ => reload() },
          "Retry"
        )
      )
    )
  }

  // Default refresh indicator
  protected def renderRefreshIndicator(): Element = {
    div(
      className := "refresh-indicator",
      position := "absolute",
      top := "10px",
      right := "10px",
      zIndex := "1000",
      cds"loading"(strattr("type") := "small")
    )
  }
}
