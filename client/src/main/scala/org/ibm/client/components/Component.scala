package org.ibm.client.components

import com.raquo.laminar.codecs.StringAsIsCodec
import com.raquo.laminar.api.L.{*, given}
import com.raquo.laminar.nodes.ReactiveHtmlElement
import org.scalajs.dom

trait Component {
  def strattr(string: String) = htmlAttr(string, StringAsIsCodec)
  def render: ReactiveHtmlElement.Base
}
