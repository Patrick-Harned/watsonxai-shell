package org.ibm.client.components
import com.raquo.laminar.api.L._

extension (sc: StringContext)
  def cds(args: Any*) = {
    val strings = sc.parts.iterator
    val expressions = args.iterator
    val sb = StringBuilder()

    while (strings.hasNext) {
      sb.append(strings.next())
      if (expressions.hasNext) {
        sb.append(expressions.next())
      }
    }
    htmlTag("cds-" + sb.toString())
  }