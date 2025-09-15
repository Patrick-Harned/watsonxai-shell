package org.ibm.watsonxaiifm


import io.kubernetes.client.openapi.apis.{CoreV1Api, StorageV1Api}
import io.kubernetes.client.openapi.models.{V1PersistentVolumeClaim, V1StorageClass}
import io.kubernetes.client.util.Config
import org.ibm.shared.{StorageClass, PVC, CreatePVCRequest}
import io.circe.parser._
import scala.collection.JavaConverters._
import scala.util.{Try, Success, Failure}

object PVCClient {
  val apiClient = Config.defaultClient()
  val coreV1Api = new CoreV1Api(apiClient)
  val storageV1Api = new StorageV1Api(apiClient)

  private val WATSONX_PVC_LABEL = "watsonxai/pvc"
  private val NAMESPACE = "cpd"

  // Get PVCs with watsonxai/pvc=true label
  def getWatsonxPVCs: List[PVC] = {
    Try {
      val labelSelector = s"$WATSONX_PVC_LABEL=true"
      val result = coreV1Api.listNamespacedPersistentVolumeClaim(
        NAMESPACE
      ).labelSelector(labelSelector).execute()

      result.getItems.asScala.toList.flatMap { k8sPvc =>
        convertK8sPvcToPVC(k8sPvc)
      }
    }.fold(
      ex => {
        println(s"Failed to get WatsonX PVCs: ${ex.getMessage}")
        ex.printStackTrace()
        List.empty
      },
      identity
    )
  }

  // Create PVC with watsonxai/pvc=true label
  def createWatsonxPVC(request: CreatePVCRequest): Option[PVC] = {
    Try {
      // Convert request to Kubernetes PVC object
      val k8sPvc = createK8sPvcFromRequest(request)

      val result = coreV1Api.createNamespacedPersistentVolumeClaim(
        NAMESPACE, k8sPvc
      ).execute()

      convertK8sPvcToPVC(result)
    }.fold(
      ex => {
        println(s"Failed to create PVC: ${ex.getMessage}")
        ex.printStackTrace()
        None
      },
      identity
    )
  }

  // Delete PVC by name (only if it has watsonxai/pvc=true label)
  def deleteWatsonxPVC(name: String): Boolean = {
    Try {
      // First check if PVC exists and has the right label
      val pvc = coreV1Api.readNamespacedPersistentVolumeClaim(name, NAMESPACE).execute()
      val labels = Option(pvc.getMetadata.getLabels).map(_.asScala.toMap).getOrElse(Map.empty)

      if (labels.get(WATSONX_PVC_LABEL).contains("true")) {
        coreV1Api.deleteNamespacedPersistentVolumeClaim(name, NAMESPACE)
        true
      } else {
        println(s"PVC $name does not have required label $WATSONX_PVC_LABEL=true")
        false
      }
    }.fold(
      ex => {
        println(s"Failed to delete PVC $name: ${ex.getMessage}")
        ex.printStackTrace()
        false
      },
      identity
    )
  }

  // Get all storage classes
  def getStorageClasses: List[StorageClass] = {
    Try {
      val result = storageV1Api.listStorageClass().execute()
      result.getItems.asScala.toList.flatMap { k8sSc =>
        convertK8sStorageClassToStorageClass(k8sSc)
      }
    }.fold(
      ex => {
        println(s"Failed to get storage classes: ${ex.getMessage}")
        ex.printStackTrace()
        List.empty
      },
      identity
    )
  }

  // Helper methods to convert between K8s objects and our domain models
  private def convertK8sPvcToPVC(k8sPvc: V1PersistentVolumeClaim): Option[PVC] = {
    Try {
      val metadata = k8sPvc.getMetadata
      val spec = k8sPvc.getSpec
      val status = k8sPvc.getStatus

      PVC(
        metadata = org.ibm.shared.PVCMetadata(
          name = metadata.getName,
          namespace = metadata.getNamespace,
          uid = Option(metadata.getUid),
          resourceVersion = Option(metadata.getResourceVersion),
          creationTimestamp = Option(metadata.getCreationTimestamp).map(_.toString),
          annotations = Option(metadata.getAnnotations).map(_.asScala.toMap),
          labels = Option(metadata.getLabels).map(_.asScala.toMap),
          finalizers = Option(metadata.getFinalizers).map(_.asScala.toList)
        ),
        spec = org.ibm.shared.PVCSpec(
          accessModes = spec.getAccessModes.asScala.toList,
          resources = org.ibm.shared.PVCResources(
            requests = org.ibm.shared.PVCResourceRequests(
              storage = spec.getResources.getRequests.get("storage").toSuffixedString
            )
          ),
          storageClassName = spec.getStorageClassName,
          volumeMode = Option(spec.getVolumeMode),
          volumeName = Option(spec.getVolumeName)
        ),
        status = Option(status).map { s =>
          org.ibm.shared.PVCStatus(
            phase = Option(s.getPhase),
            accessModes = Option(s.getAccessModes).map(_.asScala.toList),
            capacity = Option(s.getCapacity).map(_.asScala.toMap.view.mapValues(_.toSuffixedString).toMap)
          )
        }
      )
    }.toOption
  }

  private def convertK8sStorageClassToStorageClass(k8sSc: V1StorageClass): Option[StorageClass] = {
    Try {
      val metadata = k8sSc.getMetadata

      StorageClass(
        metadata = org.ibm.shared.StorageClassMetadata(
          name = metadata.getName,
          uid = Option(metadata.getUid),
          resourceVersion = Option(metadata.getResourceVersion),
          creationTimestamp = Option(metadata.getCreationTimestamp).map(_.toString),
          annotations = Option(metadata.getAnnotations).map(_.asScala.toMap)
        ),
        provisioner = k8sSc.getProvisioner,
        parameters = Option(k8sSc.getParameters).map(_.asScala.toMap),
        reclaimPolicy = Option(k8sSc.getReclaimPolicy),
        allowVolumeExpansion = Option(k8sSc.getAllowVolumeExpansion),
        volumeBindingMode = Option(k8sSc.getVolumeBindingMode)
      )
    }.toOption
  }

  private def createK8sPvcFromRequest(request: CreatePVCRequest): V1PersistentVolumeClaim = {
    import io.kubernetes.client.openapi.models._
    import io.kubernetes.client.custom.Quantity

    // Merge request labels with required watsonx label
    val allLabels = request.labels.getOrElse(Map.empty) ++ Map(WATSONX_PVC_LABEL -> "true")

    new V1PersistentVolumeClaim()
      .metadata(
        new V1ObjectMeta()
          .name(request.name)
          .namespace(NAMESPACE)
          .labels(allLabels.asJava)
      )
      .spec(
        new V1PersistentVolumeClaimSpec()
          .accessModes(request.accessModes.asJava)
          .storageClassName(request.storageClassName)
          .volumeMode("Filesystem")
          .resources(
            new V1VolumeResourceRequirements()
              .requests(Map("storage" -> Quantity.fromString(request.size)).asJava)
          )
      )
  }
}
