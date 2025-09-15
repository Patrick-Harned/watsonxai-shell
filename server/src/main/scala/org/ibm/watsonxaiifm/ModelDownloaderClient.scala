package org.ibm.modeldownloader

import io.kubernetes.client.openapi.apis.CoreV1Api
import io.kubernetes.client.openapi.models.*
import io.kubernetes.client.util.Config
import io.circe.parser.*
import io.circe.{HCursor, Json}
import com.fasterxml.jackson.databind.{ObjectMapper, SerializationFeature}
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import org.ibm.shared.{ModelDownloaderPod, PodStatus}

import scala.util.{Failure, Success, Try}
import java.time.Instant
import java.util.UUID
import scala.jdk.CollectionConverters.*

object ModelDownloaderClient {
  // 1) Setup Kubernetes API client and Jackson mapper
  private val apiClient = Config.defaultClient()
  private val coreV1Api = new CoreV1Api(apiClient)
  private val jackson = new ObjectMapper()
    .registerModule(new JavaTimeModule())
    .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)

  // 7) Get all model downloader pods in a namespace
  def getModelDownloaderPods(namespace: String = "default"): Try[List[ModelDownloaderPod]] = Try {
    val labelSelector = "watsonxai/model=true"

    val podList = coreV1Api.listNamespacedPod(
      namespace


    ).labelSelector(labelSelector).execute()

    podList.getItems.asScala.toList.map { pod =>
      val podJson = jackson.writeValueAsString(pod)
      parsePodFromJson(podJson) match {
        case Success(modelPod) => modelPod
        case Failure(ex) => throw new RuntimeException(s"Failed to parse pod ${pod.getMetadata.getName}: ${ex.getMessage}")
      }
    }
  }

  // 8) Get all model downloader pods across all accessible namespaces
  def getAllModelDownloaderPods(namespace: String = "cpd"): Try[List[ModelDownloaderPod]] = Try {
    val labelSelector = "watsonxai/model=true"

    val podList = coreV1Api.listNamespacedPod(
      namespace


    ).labelSelector(labelSelector).execute()

    podList.getItems.asScala.toList.map { pod =>
      val podJson = jackson.writeValueAsString(pod)
      parsePodFromJson(podJson) match {
        case Success(modelPod) => modelPod
        case Failure(ex) => throw new RuntimeException(s"Failed to parse pod ${pod.getMetadata.getName}: ${ex.getMessage}")
      }
    }
  }

  // 9) Get pods by status phase
  def getModelDownloaderPodsByStatus(
                                      phase: String,
                                      namespace: String = "default"
                                    ): Try[List[ModelDownloaderPod]] = {
    getModelDownloaderPods(namespace).map { pods =>
      pods.filter(_.status.exists(_.phase == phase))
    }
  }

  // 10) Get running model downloader pods
  def getRunningModelDownloaderPods(namespace: String = "default"): Try[List[ModelDownloaderPod]] = {
    getModelDownloaderPodsByStatus("Running", namespace)
  }

  // 11) Get completed model downloader pods (both succeeded and failed)
  def getCompletedModelDownloaderPods(namespace: String = "default"): Try[List[ModelDownloaderPod]] = {
    getModelDownloaderPods(namespace).map { pods =>
      pods.filter(_.status.exists(s => s.phase == "Succeeded" || s.phase == "Failed"))
    }
  }

  private def normalizeForLabel(input: String): String = {
    input
      .replace("/", "_") // Replace forward slash with underscore
      .replace(":", "_") // Replace colon with underscore  
      .replace(" ", "_") // Replace spaces with underscore
      .replaceAll("[^A-Za-z0-9\\-_.]", "_") // Replace any other invalid chars with underscore
      .replaceAll("_{2,}", "_") // Replace multiple consecutive underscores with single underscore
      .stripPrefix("_") // Remove leading underscore if any
      .stripSuffix("_") // Remove trailing underscore if any
      .take(63) // Kubernetes labels have max length of 63 chars
  }

  // 2) Create a new model downloader pod
  // Updated createModelDownloaderPod method
  def createModelDownloaderPod(
                                pvcName: String,
                                modelRepo: String,
                                localDirName: String,
                                namespace: String = "default"
                              ): Try[ModelDownloaderPod] = Try {

    val podName = s"model-downloader-${UUID.randomUUID().toString.take(8)}"

    // Create a cleaner pod spec without potential overhead issues
    val pod = new V1Pod()
    val repo = normalizeForLabel(modelRepo)

    // Set metadata
    val metadata = new V1ObjectMeta()
      .name(podName)
      .namespace(namespace)
      .putLabelsItem("app", "model-downloader")
      .putLabelsItem("created-by", "model-downloader-client")
      .putLabelsItem("watsonxai/model", "true")
      .putLabelsItem("model-repo", normalizeForLabel(modelRepo))

    pod.setMetadata(metadata)

    // Create container spec
    val container = new V1Container()
      .name("downloader")
      .image("registry.redhat.io/ubi8/python-311:latest") // External registry image
      .command(List("/bin/bash").asJava)
      .args(List("-c", createDownloadScript(modelRepo, localDirName)).asJava)
      .securityContext(new V1SecurityContext()
        .runAsUser(1001L) // Run as the default Python image user
        .allowPrivilegeEscalation(false)
        .capabilities(new V1Capabilities()
          .drop(List("ALL").asJava)))
    // Add volume mount
    container.addVolumeMountsItem(new V1VolumeMount()
      .name("models-storage")
      .mountPath("/models"))

    // Set resource requirements - simplified
    val resources = new V1ResourceRequirements()
    resources.putRequestsItem("memory", new io.kubernetes.client.custom.Quantity("512Mi"))
    resources.putRequestsItem("cpu", new io.kubernetes.client.custom.Quantity("250m"))
    resources.putLimitsItem("memory", new io.kubernetes.client.custom.Quantity("2Gi"))
    resources.putLimitsItem("cpu", new io.kubernetes.client.custom.Quantity("1000m"))
    container.setResources(resources)

    // Create pod spec
    val podSpec = new V1PodSpec()
      .restartPolicy("Never")
      .addContainersItem(container)
      .addImagePullSecretsItem(new V1LocalObjectReference().name("model-downloader-registry-secret")) // Add pull secret
      .securityContext(new V1PodSecurityContext()
        .fsGroup(1001L) // Set filesystem group to match container user
        .runAsNonRoot(true)) // Explicitly run as non-root for security

    // Add volume
    podSpec.addVolumesItem(new V1Volume()
      .name("models-storage")
      .persistentVolumeClaim(new V1PersistentVolumeClaimVolumeSource()
        .claimName(pvcName)))

    // Explicitly ensure no overhead or runtimeClassName is set
    podSpec.setRuntimeClassName(null)
    podSpec.setOverhead(null)

    pod.setSpec(podSpec)

    val createdPod = coreV1Api.createNamespacedPod(namespace, pod).execute()

    ModelDownloaderPod(
      name = podName,
      pvcName = pvcName,
      modelRepo = modelRepo,
      localDirName = localDirName,
      namespace = namespace,
      createdAt = Some(Instant.now())
    )
  }


  // 3) Fetch pod status as raw JSON and parse into domain model
  def getPodStatus(podName: String, namespace: String = "default"): Try[ModelDownloaderPod] = {
    fetchPodRawJson(podName, namespace).flatMap { json =>
      parsePodFromJson(json)
    }
  }

  // 4) Delete the pod
  def deletePod(podName: String, namespace: String = "default"): Try[Unit] = Try {
    coreV1Api.deleteNamespacedPod(podName, namespace).execute()
  }

  // 5) Check if pod is completed (succeeded or failed)
  def isPodCompleted(podName: String, namespace: String = "default"): Try[Boolean] = {
    getPodStatus(podName, namespace).map { pod =>
      pod.status.exists(s => s.phase == "Succeeded" || s.phase == "Failed")
    }
  }

  // 6) Wait for pod completion with polling
  def waitForCompletion(
                         podName: String,
                         namespace: String = "default",
                         maxWaitSeconds: Int = 3600,
                         pollIntervalSeconds: Int = 10
                       ): Try[ModelDownloaderPod] = Try {
    val startTime = System.currentTimeMillis()
    val maxWaitMillis = maxWaitSeconds * 1000L

    while (System.currentTimeMillis() - startTime < maxWaitMillis) {
      getPodStatus(podName, namespace) match {
        case Success(pod) if pod.status.exists(_.phase == "Succeeded") => return Success(pod)
        case Success(pod) if pod.status.exists(_.phase == "Failed") =>
          throw new RuntimeException(s"Pod failed: ${pod.status.flatMap(_.message).getOrElse("Unknown error")}")
        case Success(_) => // Still running, continue polling
        case Failure(ex) => throw ex
      }
      Thread.sleep(pollIntervalSeconds * 1000)
    }
    throw new RuntimeException(s"Pod did not complete within $maxWaitSeconds seconds")
  }.flatten

  // Helper: Fetch pod as raw JSON
  private def fetchPodRawJson(podName: String, namespace: String): Try[String] = Try {
    val pod: java.lang.Object = coreV1Api.readNamespacedPodStatus(podName, namespace).execute()
    jackson.writeValueAsString(pod)
  }

  // Helper: Parse pod JSON into domain model - FIXED VERSION

  // Helper: Parse pod JSON into domain model - FIXED VERSION
  private def parsePodFromJson(json: String): Try[ModelDownloaderPod] = {
    parse(json) match {
      case Left(err) =>
        Failure(new RuntimeException(s"Failed to parse JSON: $err"))
      case Right(jsonObj) =>
        Try {
          val cursor = jsonObj.hcursor

          // Extract metadata
          val podName = cursor.downField("metadata").downField("name").as[String]
            .getOrElse("unknown")
          val namespace = cursor.downField("metadata").downField("namespace").as[String]
            .getOrElse("default")

          // Extract labels to get model info
          val labels = cursor.downField("metadata").downField("labels")
          val modelRepo = labels.downField("model-repo").as[String].getOrElse("unknown")
          val localDirName = labels.downField("local-dir-name").as[String].getOrElse("unknown")

          // Extract PVC name from volumes
          val pvcName = cursor.downField("spec").downField("volumes")
            .downArray.downField("persistentVolumeClaim").downField("claimName")
            .as[String].getOrElse("unknown")

          // Extract status
          val statusCursor = cursor.downField("status")
          val phase = statusCursor.downField("phase").as[String].getOrElse("Unknown")
          val message = statusCursor.downField("message").as[String].toOption
          val reason = statusCursor.downField("reason").as[String].toOption

          // Extract timestamps
          val startTime = statusCursor.downField("startTime").as[String].toOption
            .flatMap(s => Try(Instant.parse(s)).toOption)
          val completionTime = statusCursor.downField("completionTime").as[String].toOption
            .flatMap(s => Try(Instant.parse(s)).toOption)

          val creationTimestamp = cursor.downField("metadata").downField("creationTimestamp")
            .as[String].toOption.flatMap(s => Try(Instant.parse(s)).toOption)

          ModelDownloaderPod(
            name = podName,
            pvcName = pvcName,
            modelRepo = modelRepo,
            localDirName = localDirName,
            namespace = namespace,
            status = Some(PodStatus(phase, message, reason, startTime, completionTime)),
            createdAt = creationTimestamp
          )
        }.recover {
          case ex =>
            throw new RuntimeException(s"Failed to extract pod data: ${ex.getMessage}\nRaw JSON: $json")
        }
    }
  }

  // Helper: Create the download script
  private def createDownloadScript(modelRepo: String, localDirName: String): String = {
    s"""
       |echo "Installing huggingface-hub..."
       |pip3 install huggingface-hub
       |
       |echo "Creating model directory..."
       |mkdir -p /models/$localDirName
       |
       |echo "Downloading model..."
       |huggingface-cli download $modelRepo \\
       |  --local-dir /models/$localDirName \\
       |  --local-dir-use-symlinks False
       |
       |echo "Download completed successfully!"
    """.stripMargin
  }
}
