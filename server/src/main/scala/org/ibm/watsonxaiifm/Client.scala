package org.ibm.watsonxaiifm

import io.kubernetes.client.openapi.apis.CustomObjectsApi
import io.kubernetes.client.util.Config
import cats.effect.IO
import scala.collection.JavaConverters.mapAsScalaMapConverter
import scala.util.Try

object Client {
  // Connect to OpenShift using ~/.kube/config
  val apiClient = Config.defaultClient()
  val customObjectsApi = new CustomObjectsApi(apiClient)

  // Retrieve the watsonxaiifm CR
  def getWatsonxAIIFM: Map[String, Any] = {
    Try {
      val result = customObjectsApi.getNamespacedCustomObject(
        "watsonxaiifm.cpd.ibm.com",
        "v1beta1",
        "cpd",
        "watsonxaiifm",
        "watsonxaiifm-cr"
      ).execute()

      println("WatsonX AI IFM Custom Resource:")
      println("=" * 60)
      println(result.toString)  // Simple but readable
      println("=" * 60)

      result.asInstanceOf[java.util.Map[String, Any]].asScala.toMap
    }.fold(
      ex => {
        println(s"Failed fetching: ${ex.getMessage}")
        Map.empty
      },
      v => v
    )
  }

}