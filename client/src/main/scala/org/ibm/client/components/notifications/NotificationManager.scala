package org.ibm.client.components.notifications


import com.raquo.laminar.api.L.*
import org.ibm.client.components.{Component, cds}
import org.scalajs.dom

import scala.concurrent.duration.*

case class Notification(
                         id: String,
                         kind: String, // "success", "error", "warning", "info"
                         title: String,
                         subtitle: String,
                         timestamp: Long = System.currentTimeMillis()
                       )

object NotificationManager extends Component{
  private val notificationsVar = Var[List[Notification]](List.empty)

  def showSuccess(subtitle: String, title: String = "Success"): Unit = {
    addNotification(Notification(
      id = java.util.UUID.randomUUID().toString,
      kind = "success",
      title = title,
      subtitle = subtitle
    ))
  }

  def showError(subtitle: String, title: String = "Error"): Unit = {
    addNotification(Notification(
      id = java.util.UUID.randomUUID().toString,
      kind = "error",
      title = title,
      subtitle = subtitle
    ))
  }
  private def addNotification(notification: Notification): Unit = {
    notificationsVar.update(_ :+ notification)

    // Auto-remove after 5 seconds using JavaScript setTimeout
    dom.window.setTimeout(() => {
      removeNotification(notification.id)
    }, 5000.0)
  }


  def removeNotification(id: String): Unit = {
    notificationsVar.update(_.filterNot(_.id == id))
  }

  def render: Element = {
    div(
      className := "notification-container",
      children <-- notificationsVar.signal.map { notifications =>
        notifications.map(renderNotification)
      }
    )
  }

  private def renderNotification(notification: Notification): Element = {
    cds"inline-notification"(
      strattr("kind") := notification.kind,
      strattr("title") := notification.title,
      strattr("subtitle") := notification.subtitle,
      className := "notification-item",

      // Close button
      cds"inline-notification-button"(
        onClick --> { _ => removeNotification(notification.id) }
      )
    )
  }
}
