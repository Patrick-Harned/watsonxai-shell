package org.ibm.client.api


import com.raquo.laminar.api.L.*
import org.ibm.shared.{ConsoleInfo, CreateModelDownloaderRequest, ModelDownloaderPod, PVC}
import sttp.client3.*
import sttp.client3.circe.*

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

object AppDataContext extends ApiService {

  // Shared state variables
  val modelDownloadersVar = Var[List[ModelDownloaderPod]](List.empty)
  val pvcsVar = Var[List[PVC]](List.empty)
  val consoleUrlVar = Var[String]("")

  // Loading states
  val isLoadingModelDownloadersVar = Var(false)
  val isLoadingPvcsVar = Var(false)
  val isLoadingConsoleUrlVar = Var(false)

  // Error states  
  val modelDownloadersErrorVar = Var[Option[String]](None)
  val pvcsErrorVar = Var[Option[String]](None)
  val consoleUrlErrorVar = Var[Option[String]](None)

  // Signals for reactive components
  val modelDownloadersSignal: Signal[List[ModelDownloaderPod]] = modelDownloadersVar.signal
  val pvcsSignal: Signal[List[PVC]] = pvcsVar.signal
  val consoleUrlSignal: Signal[String] = consoleUrlVar.signal

  val isLoadingModelDownloadersSignal: Signal[Boolean] = isLoadingModelDownloadersVar.signal
  val isLoadingPvcsSignal: Signal[Boolean] = isLoadingPvcsVar.signal
  val isLoadingConsoleUrlSignal: Signal[Boolean] = isLoadingConsoleUrlVar.signal

  val modelDownloadersErrorSignal: Signal[Option[String]] = modelDownloadersErrorVar.signal
  val pvcsErrorSignal: Signal[Option[String]] = pvcsErrorVar.signal
  val consoleUrlErrorSignal: Signal[Option[String]] = consoleUrlErrorVar.signal

  // API Methods

  def fetchModelDownloaders(namespace: Option[String] = None): Unit = {
    isLoadingModelDownloadersVar.set(true)
    modelDownloadersErrorVar.set(None)

    val uri = namespace match {
      case Some(ns) => uri"/api/watsonxai/modeldownloaders?namespace=$ns"
      case None => uri"/api/watsonxai/modeldownloaders"
    }

    handleApiResponse(
      basicRequest.get(uri).response(asJson[List[ModelDownloaderPod]]).send(backend),
      onSuccess = { pods =>
        modelDownloadersVar.set(pods)
        isLoadingModelDownloadersVar.set(false)
      },
      onError = { error =>
        modelDownloadersErrorVar.set(Some(s"Failed to load model downloaders: $error"))
        isLoadingModelDownloadersVar.set(false)
      }
    )
  }

  def fetchPvcs(): Unit = {
    isLoadingPvcsVar.set(true)
    pvcsErrorVar.set(None)

    handleApiResponse(
      basicRequest.get(uri"/api/watsonxai/pvcs").response(asJson[List[PVC]]).send(backend),
      onSuccess = { pvcs =>
        pvcsVar.set(pvcs)
        isLoadingPvcsVar.set(false)
      },
      onError = { error =>
        pvcsErrorVar.set(Some(s"Failed to load PVCs: $error"))
        isLoadingPvcsVar.set(false)
      }
    )
  }

  def fetchConsoleUrl(): Unit = {
    isLoadingConsoleUrlVar.set(true)
    consoleUrlErrorVar.set(None)

    handleApiResponse(
      basicRequest.get(uri"/api/watsonxai/console-url").response(asJson[ConsoleInfo]).send(backend),
      onSuccess = { consoleInfo =>
        consoleUrlVar.set(consoleInfo.url)
        isLoadingConsoleUrlVar.set(false)
      },
      onError = { error =>
        consoleUrlErrorVar.set(Some(s"Failed to load console URL: $error"))
        // Set fallback URL
        consoleUrlVar.set("https://console-openshift-console.apps.your-cluster.com")
        isLoadingConsoleUrlVar.set(false)
      }
    )
  }

  def createModelDownloader(request: CreateModelDownloaderRequest): Future[Either[String, ModelDownloaderPod]] = {
    basicRequest
      .post(uri"/api/watsonxai/modeldownloaders")
      .body(request)
      .response(asJson[ModelDownloaderPod])
      .send(backend)
      .map(_.body match {
        case Right(pod) =>
          // Refresh the list after creation
          fetchModelDownloaders()
          Right(pod)
        case Left(error) => Left(error.toString)
      })
  }

  def deleteModelDownloader(name: String, namespace: String = "default"): Future[Either[String, Unit]] = {
    basicRequest
      .delete(uri"/api/watsonxai/modeldownloaders/$name?namespace=$namespace")
      .response(asString)
      .send(backend)
      .map(_.body match {
        case Right(_) =>
          // Refresh the list after deletion
          fetchModelDownloaders()
          Right(())
        case Left(error) => Left(error)
      })
  }

  def refreshPodStatus(name: String, namespace: String = "default"): Unit = {
    handleApiResponse(
      basicRequest
        .get(uri"/api/watsonxai/modeldownloaders/$name/status?namespace=$namespace")
        .response(asJson[ModelDownloaderPod])
        .send(backend),
      onSuccess = { pod =>
        // Update the specific pod in the list
        val updatedList = modelDownloadersVar.now().map { existingPod =>
          if (existingPod.name == name && existingPod.namespace == namespace) pod
          else existingPod
        }
        modelDownloadersVar.set(updatedList)
      },
      onError = { error =>
        println(s"Failed to refresh status for $name: $error")
      }
    )
  }

  // Utility methods
  def getModelDownloaderLogUrl(podName: String, namespace: String): String = {
    s"${consoleUrlVar.now()}/k8s/ns/$namespace/pods/$podName/logs"
  }

  def init(): Unit = {
    // Initialize all data on app start
    fetchConsoleUrl()
    fetchModelDownloaders()
    fetchPvcs()
  }

  // Refresh all data
  def refreshAll(): Unit = {
    fetchModelDownloaders()
    fetchPvcs()
    fetchConsoleUrl()
  }
}
