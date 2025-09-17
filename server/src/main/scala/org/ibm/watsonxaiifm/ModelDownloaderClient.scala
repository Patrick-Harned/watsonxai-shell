package org.ibm.watsonxaiifm

import io.kubernetes.client.openapi.apis.CoreV1Api
import io.kubernetes.client.openapi.models.*
import io.kubernetes.client.util.Config
import io.circe.parser.*
import io.circe.{HCursor, Json}
import com.fasterxml.jackson.databind.{ObjectMapper, SerializationFeature}
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import org.ibm.database.Database
import org.ibm.shared.{DownloadedModel, ModelDownloaderPod, PodStatus}

import scala.util.{Failure, Success, Try}
import java.time.Instant
import java.util.UUID
import scala.jdk.CollectionConverters.*
import java.util.concurrent.{Executors, TimeUnit}
import scala.concurrent.ExecutionContext // Required for Futures if you use them elsewhere

object ModelDownloaderClient {
  // 1) Setup Kubernetes API client and Jackson mapper
  private val apiClient = Config.defaultClient()
  private val coreV1Api = new CoreV1Api(apiClient)
  private val jackson = new ObjectMapper()
    .registerModule(new JavaTimeModule())
    .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)

  // --- DATABASE INTEGRATION ---

  // --- RECONCILIATION SCHEDULER ---
  private val reconciliationScheduler = Executors.newSingleThreadScheduledExecutor()
  private val RECONCILE_INTERVAL_SECONDS = 30 // How often to check Kubernetes and sync DB

  // Start the reconciliation loop on object initialization
  startReconciliationLoop()

  // 7) Get all model downloader pods in a namespace (updated to return ModelDownloaderPod directly)
  def getModelDownloaderPods(namespace: String = "default"): Try[List[ModelDownloaderPod]] = Try {
    val labelSelector = "watsonxai/model=true"

    val podList = coreV1Api.listNamespacedPod(
      namespace
    ).labelSelector(labelSelector).execute()

    podList.getItems.asScala.toList.flatMap { pod =>
      val podJson = jackson.writeValueAsString(pod)
      parsePodFromJson(podJson) match {
        case Success(modelPod) => Some(modelPod)
        case Failure(ex) =>
          System.err.println(s"Failed to parse pod ${pod.getMetadata.getName}: ${ex.getMessage}")
          None // Skip pods that can't be parsed
      }
    }
  }

  // 8) Get all model downloader pods across all accessible namespaces (using the updated getModelDownloaderPods)
  def getAllModelDownloaderPods(namespace: String = "cpd"): Try[List[ModelDownloaderPod]] =
    getModelDownloaderPods(namespace) // Assumes 'cpd' is a specific namespace to search, or "all namespaces" if the API allowed it.
  // The current Kubernetes client `listNamespacedPod` is namespaced.
  // To truly get "all accessible namespaces," you'd need to iterate or use a cluster-scoped list if available.
  // For now, it delegates to getModelDownloaderPods, keeping the 'cpd' default for labels.

  // 9) Get pods by status phase - unchanged
  def getModelDownloaderPodsByStatus(
                                      phase: String,
                                      namespace: String = "default"
                                    ): Try[List[ModelDownloaderPod]] = {
    getModelDownloaderPods(namespace).map { pods =>
      pods.filter(_.status.exists(_.phase == phase))
    }
  }

  // 10) Get running model downloader pods - unchanged
  def getRunningModelDownloaderPods(namespace: String = "default"): Try[List[ModelDownloaderPod]] = {
    getModelDownloaderPodsByStatus("Running", namespace)
  }

  // 11) Get completed model downloader pods (both succeeded and failed) - unchanged
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
  // Updated createModelDownloaderPod method to store initial record
  def createModelDownloaderPod(
                                pvcName: String,
                                modelRepo: String,
                                localDirName: String,
                                namespace: String = "default"
                              ): Try[ModelDownloaderPod] = Try {

    val podName = s"model-downloader-${UUID.randomUUID().toString.take(8)}"
    val trackedModelId = UUID.randomUUID().toString // Unique ID for database tracking

    // --- Start: Kubernetes Pod Creation Logic ---
    val pod = new V1Pod()

    val metadata = new V1ObjectMeta()
      .name(podName)
      .namespace(namespace)
      .putLabelsItem("app", "model-downloader")
      .putLabelsItem("created-by", "model-downloader-client")
      .putLabelsItem("watsonxai/model", "true")
      .putLabelsItem("model-repo", normalizeForLabel(modelRepo))
      .putLabelsItem("local-dir-name", normalizeForLabel(localDirName))
      .putLabelsItem("tracked-model-id", trackedModelId) // Add database ID as a label for easier lookup

    pod.setMetadata(metadata)

    val container = new V1Container()
      .name("downloader")
      .image("registry.redhat.io/ubi8/python-311:latest")
      .command(List("/bin/bash").asJava)
      .args(List("-c", createDownloadScript(modelRepo, localDirName)).asJava)
      .securityContext(new V1SecurityContext()
        .runAsUser(1001L)
        .allowPrivilegeEscalation(false)
        .capabilities(new V1Capabilities()
          .drop(List("ALL").asJava)))
    container.addVolumeMountsItem(new V1VolumeMount()
      .name("models-storage")
      .mountPath("/models"))

    val resources = new V1ResourceRequirements()
    resources.putRequestsItem("memory", new io.kubernetes.client.custom.Quantity("512Mi"))
    resources.putRequestsItem("cpu", new io.kubernetes.client.custom.Quantity("250m"))
    resources.putLimitsItem("memory", new io.kubernetes.client.custom.Quantity("2Gi"))
    resources.putLimitsItem("cpu", new io.kubernetes.client.custom.Quantity("1000m"))
    container.setResources(resources)

    val podSpec = new V1PodSpec()
      .restartPolicy("Never")
      .addContainersItem(container)
      .addImagePullSecretsItem(new V1LocalObjectReference().name("model-downloader-registry-secret"))
      .securityContext(new V1PodSecurityContext()
        .fsGroup(1001L)
        .runAsNonRoot(true))

    podSpec.addVolumesItem(new V1Volume()
      .name("models-storage")
      .persistentVolumeClaim(new V1PersistentVolumeClaimVolumeSource()
        .claimName(pvcName)))

    podSpec.setRuntimeClassName(null)
    podSpec.setOverhead(null)

    pod.setSpec(podSpec)

    val createdPod = coreV1Api.createNamespacedPod(namespace, pod).execute()
    // --- End: Kubernetes Pod Creation Logic ---

    // --- Start: Database Integration (initial record) ---
    val newDownloadedModel = DownloadedModel(
      id = trackedModelId,
      modelRepo = modelRepo,
      localDirName = localDirName,
      pvcName = pvcName,
      downloadTime = Instant.now(),
      status = "PENDING", // Initial status
      downloadPodName = Some(podName)
    )

    Database.addModel(newDownloadedModel) match {
      case Success(_) => println(s"Model download for ${modelRepo} tracked in DB with ID: $trackedModelId")
      case Failure(ex) => System.err.println(s"Failed to track model download in DB: ${ex.getMessage}. Pod $podName might not be tracked.")
    }
    // --- End: Database Integration ---

    // No need to start individual polling threads here, reconciliation loop handles it.

    ModelDownloaderPod(
      name = podName,
      pvcName = pvcName,
      modelRepo = modelRepo,
      localDirName = localDirName,
      namespace = namespace,
      createdAt = Some(Instant.now()),
      trackedModelId = Some(trackedModelId) // Include the DB ID in the returned pod object
    )
  }

  /**
   * Starts the background reconciliation loop to synchronize Kubernetes pod status with the database.
   */
  private def startReconciliationLoop(): Unit = {
    reconciliationScheduler.scheduleAtFixedRate(
      () => {
        println(s"Reconciliation loop: Checking Kubernetes for model downloader pods...")
        reconcilePodsAndDatabase()
      },
      0, // Initial delay
      RECONCILE_INTERVAL_SECONDS,
      TimeUnit.SECONDS
    )
    println(s"ModelDownloaderClient reconciliation loop started. Polling every $RECONCILE_INTERVAL_SECONDS seconds.")
  }

  /**
   * Performs the reconciliation:
   * 1. Fetches all active model downloader pods from Kubernetes.
   * 2. Fetches all tracked models from the database.
   * 3. Compares states and updates the database.
   */
  private def reconcilePodsAndDatabase(): Unit = {
    val k8sPodsResult = getAllModelDownloaderPods() // Or iterate over relevant namespaces
    k8sPodsResult match {
      case Success(k8sPods) =>
        val dbModelsResult = Database.getModels
        dbModelsResult match {
          case Success(dbModels) =>
            val k8sPodMap = k8sPods.map(p => p.trackedModelId.getOrElse(p.name) -> p).toMap
            // We'll iterate through dbModels, so don't need a map for it here,
            // but keep it for K8s pods for efficient lookups.
            // Phase 1: Update existing or add new models in DB based on K8s state
            k8sPods.foreach { k8sPod =>
              val trackedModelId = k8sPod.trackedModelId.getOrElse(k8sPod.name) // Use name as fallback ID
              val k8sStatus = k8sPod.status.map(_.phase).getOrElse("UNKNOWN").toUpperCase
              Database.getModelById(trackedModelId) match { // Get model by ID from DB to check its current status
                case Success(Some(dbModel)) =>
                  // Model exists in DB, check if status needs updating
                  if (dbModel.status != k8sStatus) {
                    println(s"Reconcile: Updating DB status for model ID ${trackedModelId} (Pod: ${k8sPod.name}) from ${dbModel.status} to ${k8sStatus}")
                    Database.updateModelStatus(trackedModelId, k8sStatus) match {
                      case Failure(ex) => System.err.println(s"Reconcile: Failed to update DB status for $trackedModelId: ${ex.getMessage}")
                      case _ => // Success
                    }
                  }
                case Success(None) =>
                  // Pod exists in K8s but not in DB, add it
                  println(s"Reconcile: Found new K8s pod ${k8sPod.name} (tracked ID: ${trackedModelId}) not in DB. Adding...")
                  val newModel = DownloadedModel(
                    id = trackedModelId,
                    modelRepo = k8sPod.modelRepo,
                    localDirName = k8sPod.localDirName,
                    pvcName = k8sPod.pvcName,
                    downloadTime = k8sPod.createdAt.getOrElse(Instant.now()), // Use pod creation time
                    status = k8sStatus,
                    downloadPodName = Some(k8sPod.name)
                  )
                  Database.addModel(newModel) match {
                    case Failure(ex) => System.err.println(s"Reconcile: Failed to add new K8s pod $trackedModelId to DB: ${ex.getMessage}")
                    case _ => // Success
                  }
                case Failure(ex) => System.err.println(s"Reconcile: Error checking DB for model ID $trackedModelId: ${ex.getMessage}")
              }
            }
            // Phase 2: Identify and handle models in DB that no longer have corresponding K8s pods
            dbModels.foreach { dbModel =>
              if (!k8sPodMap.contains(dbModel.id)) { // If DB model's ID is NOT found in the current K8s pods
                // Check if the model has successfully completed its download
                if (dbModel.status == "COMPLETED") {
                  println(s"Reconcile: DB model ID ${dbModel.id} (Pod: ${dbModel.downloadPodName.getOrElse("N/A")}) completed successfully, K8s pod is gone. Retaining in DB.")
                  // No action needed: we keep successful downloads permanently.
                  // We might want to clear `downloadPodName` here if the pod is truly gone,
                  // or set its status to a final "RECORDED" state if it was "COMPLETED" before
                  // and now we're just recording that the pod is gone.
                  if (dbModel.downloadPodName.isDefined) {
                    Database.updateModelPodName(dbModel.id, None) match { // Assuming you add a `updateModelPodName` method to `Database`
                      case Failure(ex) => System.err.println(s"Reconcile: Failed to clear pod name for completed model ${dbModel.id}: ${ex.getMessage}")
                      case _ => // Success
                    }
                  }
                } else if (dbModel.status == "PENDING") {
                  // A pending model disappeared from K8s. This usually means pod creation failed or was deleted.
                  println(s"Reconcile: DB model ID ${dbModel.id} (Pod: ${dbModel.downloadPodName.getOrElse("N/A")}) was PENDING but K8s pod is gone. Marking as FAILED.")
                  Database.updateModelStatus(dbModel.id, "FAILED") match {
                    case Failure(ex) => System.err.println(s"Reconcile: Failed to update status for pending/missing model ${dbModel.id}: ${ex.getMessage}")
                    case _ => // Success
                  }
                }
                else {
                  // For any other non-COMPLETED status (e.g., FAILED, UNKNOWN, DELETED from previous logic),
                  // if the K8s pod is gone, we can now safely delete the DB record.
                  println(s"Reconcile: DB model ID ${dbModel.id} (Pod: ${dbModel.downloadPodName.getOrElse("N/A")}) status ${dbModel.status}, K8s pod is gone. Deleting from DB.")
                  Database.deleteModel(dbModel.id) match {
                    case Failure(ex) => System.err.println(s"Reconcile: Failed to delete DB model ${dbModel.id}: ${ex.getMessage}")
                    case _ => // Success
                  }
                }
              }
            }
          case Failure(ex) => System.err.println(s"Reconcile: Error fetching models from database: ${ex.getMessage}")
        }
      case Failure(ex) => System.err.println(s"Reconcile: Error fetching pods from Kubernetes: ${ex.getMessage}")
    }
  }

  // 3) Fetch pod status as raw JSON and parse into domain model - unchanged (relies on parsePodFromJson)
  def getPodStatus(podName: String, namespace: String = "default"): Try[ModelDownloaderPod] = {
    fetchPodRawJson(podName, namespace).flatMap { json =>
      parsePodFromJson(json)
    }
  }

  // 4) Delete the pod - Also update the database to reflect deletion
  def deletePod(podName: String, namespace: String = "default"): Try[Unit] = Try {
    val trackedIdFromPodLabels: Option[String] = coreV1Api.readNamespacedPod(podName, namespace).execute()
      .getMetadata.getLabels.asScala.get("tracked-model-id")
    coreV1Api.deleteNamespacedPod(podName, namespace).execute()
    println(s"Kubernetes pod $podName deleted.")
    trackedIdFromPodLabels match {
      case Some(id) =>
        // Update status to DELETED. Reconciliation will pick this up and remove the DB entry.
        Database.updateModelStatus(id, "DELETED") match {
          case Success(_) => println(s"Updated DB status for model $id to DELETED.")
          case Failure(ex) => System.err.println(s"Failed to update DB status for $id to DELETED: ${ex.getMessage}")
        }
      case None =>
        println(s"No 'tracked-model-id' label found for pod $podName. Assuming manual deletion, reconciliation will handle DB state.")
    }
  }

  // 5) Check if pod is completed (succeeded or failed) - unchanged
  def isPodCompleted(podName: String, namespace: String = "default"): Try[Boolean] = {
    getPodStatus(podName, namespace).map { pod =>
      pod.status.exists(s => s.phase == "Succeeded" || s.phase == "Failed")
    }
  }

  // 6) Wait for pod completion with polling - This synchronous method can remain for specific use cases
  //    where you need to block until a pod is finished, but it's not part of the background reconciliation.
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
        case Failure(ex) => throw ex // Re-throw exception if K8s API call itself fails
      }
      Thread.sleep(pollIntervalSeconds * 1000)
    }
    throw new RuntimeException(s"Pod did not complete within $maxWaitSeconds seconds")
  }.flatten

  // Helper: Fetch pod as raw JSON - unchanged
  private def fetchPodRawJson(podName: String, namespace: String): Try[String] = Try {
    val pod: java.lang.Object = coreV1Api.readNamespacedPodStatus(podName, namespace).execute()
    jackson.writeValueAsString(pod)
  }

  // Helper: Parse pod JSON into domain model - UPDATED to extract tracked-model-id
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
          val trackedModelId = labels.downField("tracked-model-id").as[String].toOption // Extract new label

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
            createdAt = creationTimestamp,
            trackedModelId = trackedModelId // Include the extracted ID
          )
        }.recover {
          case ex =>
            throw new RuntimeException(s"Failed to extract pod data: ${ex.getMessage}\nRaw JSON: $json")
        }
    }
  }

  // Helper: Create the download script - unchanged
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

  // Shutdown hook to cleanly stop the reconciliation scheduler
  sys.addShutdownHook {
    println("Shutting down reconciliation scheduler...")
    reconciliationScheduler.shutdown()
    if (!reconciliationScheduler.awaitTermination(5, TimeUnit.SECONDS)) {
      println("Reconciliation scheduler did not terminate cleanly, forcing shutdown.")
      reconciliationScheduler.shutdownNow()
    }
  }

  // No more `resumePollingForPendingDownloads` needed, as reconciliation handles all states.
}
