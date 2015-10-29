package springnz.sparkplug.data

import com.typesafe.config.ConfigFactory
import org.apache.spark.sql.{ DataFrame, SaveMode }
import springnz.sparkplug.core.SparkConverters._
import springnz.sparkplug.core.SparkOperation
import springnz.util.Pimpers._

import scala.util.Try

class JdbcDataFrameExporter(dataBaseName: String, tableName: String, saveMode: SaveMode) {

  protected val config = ConfigFactory.load()

  def export(dataFrame: DataFrame): SparkOperation[(DataFrame, Try[Unit])] =
    SparkOperation { _ ⇒
      val writeResult = Try {
        Class.forName(config.getString("spark.mysql.driverClass"))
        val connectionString = config.getString(s"spark.mysql.$dataBaseName.connectionString")
        val user = config.getString(s"spark.mysql.$dataBaseName.user")
        val password = config.getString(s"spark.mysql.$dataBaseName.password")
        dataFrame.write
          .mode(SaveMode.Overwrite)
          .jdbc(connectionString, tableName, Map("user" -> user, "password" -> password))
      }.withErrorLog(s"Failed to write to JDBC database '$dataBaseName', table '$tableName'")
      (dataFrame, writeResult)
    }
}
