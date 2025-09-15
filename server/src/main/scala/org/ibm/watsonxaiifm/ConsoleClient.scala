package org.ibm.watsonxaiifm

import com.fasterxml.jackson.databind.ObjectMapper
import io.circe.parser.*
import io.kubernetes.client.openapi.apis.CustomObjectsApi
import io.kubernetes.client.util.Config


object ConsoleClient {

  private val apiClient = Config.defaultClient()
  private val customObjectsApi = new CustomObjectsApi(apiClient)
  private val jackson = new ObjectMapper()

  def getOpenShiftConsoleUrl: String = {
    try {
      // Try to get console URL from cluster config
      val response = customObjectsApi.getClusterCustomObject(
        "config.openshift.io",
        "v1",
        "consoles",
        "cluster"
      ).execute()

      val json = jackson.writeValueAsString(response)

      // Parse the console URL from the cluster config
      parse(json) match {
        case Right(jsonObj) =>
          val consoleUrl = jsonObj.hcursor
            .downField("status")
            .downField("consoleURL")
            .as[String]
            .getOrElse("https://console-openshift-console.apps.your-cluster.com") // fallback
          consoleUrl
        case Left(_) =>
          "https://console-openshift-console.apps.your-cluster.com" // fallback
      }
    } catch {
      case _: Exception =>
        // Fallback URL - you might want to configure this
        "https://console-openshift-console.apps.your-cluster.com"
    }
  }

}