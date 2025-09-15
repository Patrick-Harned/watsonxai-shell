package org.ibm.tel.components

// components/modals/Modal.scala

import com.raquo.laminar.api.L.*
import org.ibm.client.components.{Component, cds}
import org.scalajs.dom

import scala.scalajs.js
import java.util.UUID

// components/modals/Modal.scala
class Modal(
             title: String,
             subtitle: String = "",
             content: Element,
             size: String = "lg",
             id: Option[String] = None // Allow custom ID
           ) extends Component {

  // Generate unique ID if not provided
  val timestamp = js.Date.now().toLong

  private val modalId = id.getOrElse(s"modal-${timestamp}")

  // Modal state
  private val isOpenVar = Var(false)
  val isOpenSignal = isOpenVar.signal

  // Public methods to control modal
  def open(): Unit = isOpenVar.set(true)
  def close(): Unit = isOpenVar.set(false)

  // The modal element
  val element: Element = {
    cds"modal"(
      idAttr := modalId, // ✅ Add unique ID to the modal
      //strattr("has-scrolling-content") :="",
      // Target THIS specific modal by ID
      isOpenSignal --> { isOpen =>
        val modalElement = dom.document.getElementById(modalId).asInstanceOf[js.Dynamic] // ✅ Use specific ID
        if (modalElement != null) {
          if (isOpen) {
            modalElement.setAttribute("open", "")
          } else {
            modalElement.removeAttribute("open")
          }
        }
      },
      strattr("size") := size,

      cds"modal-header"(
        cds"modal-close-button"(
          onClick --> (_ => close())
        ),
        cds"modal-label"("Asset Management"),
        cds"modal-heading"(title)
      ),

      cds"modal-body"(
        if (subtitle.nonEmpty) {
          cds"modal-body-content"(
            strattr("description") := "",
            subtitle
          )
        } else emptyNode,

        content // Insert the provided content
      )
    )
  }
}
