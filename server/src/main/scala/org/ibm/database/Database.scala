package org.ibm.database

// org/ibm/modeldownloader/DownloadedModelTracker.scala (New File)

import org.ibm.shared.DownloadedModel

import java.sql.{Connection, DriverManager, SQLException, Timestamp}
import scala.collection.mutable.ListBuffer
import scala.util.Try

object Database {

  // Configuration for the SQLite database file
  private val DB_FILE = "downloaded_models.db"
  private val DB_URL = s"jdbc:sqlite:$DB_FILE"

  // Initialize the database connection and schema
  initDatabase()

  /**
   * Initializes the SQLite database by ensuring the table exists.
   */
  private def initDatabase(): Unit = {
    var connection: Option[Connection] = None
    try {
      // Load the SQLite JDBC driver
      Class.forName("org.sqlite.JDBC")
      connection = Some(DriverManager.getConnection(DB_URL))
      val statement = connection.get.createStatement()

      // SQL to create the table if it doesn't exist
      val createTableSQL =
        """
          |CREATE TABLE IF NOT EXISTS downloaded_models (
          |    id TEXT PRIMARY KEY,
          |    model_repo TEXT NOT NULL,
          |    local_dir_name TEXT NOT NULL,
          |    pvc_name TEXT NOT NULL,
          |    download_time TIMESTAMP NOT NULL,
          |    status TEXT NOT NULL,
          |    download_pod_name TEXT
          |);
          |""".stripMargin
      statement.execute(createTableSQL)
      println(s"SQLite database initialized: $DB_FILE")
    } catch {
      case e: SQLException => println(s"Error initializing database: ${e.getMessage}")
      case e: ClassNotFoundException => println(s"SQLite JDBC driver not found: ${e.getMessage}")
    } finally {
      connection.foreach(_.close())
    }
  }

  /**
   * Establishes a connection to the SQLite database.
   * Call `close()` on the returned connection when done.
   */
  private def getConnection: Connection = {
    Class.forName("org.sqlite.JDBC") // Ensure driver is loaded
    DriverManager.getConnection(DB_URL)
  }

  def updateModelPodName(id: String, newPodName: Option[String]): Try[Unit] = Try {
    var connection: Option[Connection] = None
    try {
      connection = Some(getConnection)
      val sql = "UPDATE downloaded_models SET download_pod_name = ? WHERE id = ?;"
      val pstmt = connection.get.prepareStatement(sql)
      pstmt.setString(1, newPodName.orNull)
      pstmt.setString(2, id)
      val rowsAffected = pstmt.executeUpdate()
      if (rowsAffected > 0) println(s"Updated pod name for model ID $id to $newPodName")
      else println(s"No model found with ID: $id to update pod name.")
    } finally {
      connection.foreach(_.close())
    }
  }

  /**
   * Adds a new downloaded model entry to the database.
   * @param model The DownloadedModel object to add.
   * @return A Try indicating success or failure.
   */
  def addModel(model: DownloadedModel): Try[Unit] = Try {
    var connection: Option[Connection] = None
    try {
      connection = Some(getConnection)
      val sql =
        """
          |INSERT INTO downloaded_models (id, model_repo, local_dir_name, pvc_name, download_time, status, download_pod_name)
          |VALUES (?, ?, ?, ?, ?, ?, ?);
          |""".stripMargin
      val pstmt = connection.get.prepareStatement(sql)
      pstmt.setString(1, model.id)
      pstmt.setString(2, model.modelRepo)
      pstmt.setString(3, model.localDirName)
      pstmt.setString(4, model.pvcName)
      pstmt.setTimestamp(5, Timestamp.from(model.downloadTime))
      pstmt.setString(6, model.status)
      pstmt.setString(7, model.downloadPodName.orNull) // Use orNull for Option[String]
      pstmt.executeUpdate()
      println(s"Added model to tracker: ${model.modelRepo}")
    } finally {
      connection.foreach(_.close())
    }
  }

  /**
   * Retrieves all downloaded models from the database.
   * @return A Try containing a List of DownloadedModel, or a Failure if an error occurs.
   */
  def getModels: Try[List[DownloadedModel]] = Try {
    var connection: Option[Connection] = None
    val models = ListBuffer[DownloadedModel]()
    try {
      connection = Some(getConnection)
      val statement = connection.get.createStatement()
      val resultSet = statement.executeQuery("SELECT * FROM downloaded_models;")

      while (resultSet.next()) {
        models += DownloadedModel(
          id = resultSet.getString("id"),
          modelRepo = resultSet.getString("model_repo"),
          localDirName = resultSet.getString("local_dir_name"),
          pvcName = resultSet.getString("pvc_name"),
          downloadTime = resultSet.getTimestamp("download_time").toInstant,
          status = resultSet.getString("status"),
          downloadPodName = Option(resultSet.getString("download_pod_name"))
        )
      }
      models.toList
    } finally {
      connection.foreach(_.close())
    }
  }

  /**
   * Retrieves a specific downloaded model by its ID.
   * @param id The unique ID of the model.
   * @return A Try containing an Option of DownloadedModel, or a Failure.
   */
  def getModelById(id: String): Try[Option[DownloadedModel]] = Try {
    var connection: Option[Connection] = None
    try {
      connection = Some(getConnection)
      val sql = "SELECT * FROM downloaded_models WHERE id = ?;"
      val pstmt = connection.get.prepareStatement(sql)
      pstmt.setString(1, id)
      val resultSet = pstmt.executeQuery()

      if (resultSet.next()) {
        Some(DownloadedModel(
          id = resultSet.getString("id"),
          modelRepo = resultSet.getString("model_repo"),
          localDirName = resultSet.getString("local_dir_name"),
          pvcName = resultSet.getString("pvc_name"),
          downloadTime = resultSet.getTimestamp("download_time").toInstant,
          status = resultSet.getString("status"),
          downloadPodName = Option(resultSet.getString("download_pod_name"))
        ))
      } else {
        None
      }
    } finally {
      connection.foreach(_.close())
    }
  }

  /**
   * Deletes a downloaded model entry by its ID.
   * @param id The unique ID of the model to delete.
   * @return A Try indicating success or failure.
   */
  def deleteModel(id: String): Try[Unit] = Try {
    var connection: Option[Connection] = None
    try {
      connection = Some(getConnection)
      val sql = "DELETE FROM downloaded_models WHERE id = ?;"
      val pstmt = connection.get.prepareStatement(sql)
      pstmt.setString(1, id)
      val rowsAffected = pstmt.executeUpdate()
      if (rowsAffected > 0) println(s"Deleted model with ID: $id")
      else println(s"No model found with ID: $id to delete.")
    } finally {
      connection.foreach(_.close())
    }
  }

  /**
   * Updates the status of a downloaded model.
   * @param id The ID of the model to update.
   * @param newStatus The new status string.
   * @return A Try indicating success or failure.
   */
  def updateModelStatus(id: String, newStatus: String): Try[Unit] = Try {
    var connection: Option[Connection] = None
    try {
      connection = Some(getConnection)
      val sql = "UPDATE downloaded_models SET status = ? WHERE id = ?;"
      val pstmt = connection.get.prepareStatement(sql)
      pstmt.setString(1, newStatus)
      pstmt.setString(2, id)
      val rowsAffected = pstmt.executeUpdate()
      if (rowsAffected > 0) println(s"Updated status for model ID $id to $newStatus")
      else println(s"No model found with ID: $id to update status.")
    } finally {
      connection.foreach(_.close())
    }
  }

  // You might want to add other update methods if specific fields need to be changed.
}

