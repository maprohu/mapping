package hu.mapro.mapping

import java.net.URI

import slick.jdbc.JdbcBackend.Database
import slick.driver.PostgresDriver.api._

import scala.util.Properties

/**
 * Created by marci on 23-09-2015.
 */
object DB {

  val urlString = Properties.envOrElse("DATABASE_URL", "postgres://mapping:mapping@localhost/mapping")

  val dbUri = new URI(urlString)

  val username = dbUri.getUserInfo().split(":")(0)
  val password = dbUri.getUserInfo().split(":")(1)
  val dbUrl = s"jdbc:postgresql://${dbUri.getHost()}:${dbUri.getPort()}${dbUri.getPath()}"

  Database.forURL(
    url = dbUrl,
    user = username,
    password = password,
    driver = "org.postgresql.Driver"
  )

  class GpsData(tag: Tag) extends Table[(Int, Array[Byte])](tag, "GPS_DATA") {
    def id = column[Int]("ID", O.PrimaryKey)
    def data = column[Array[Byte]]("DATA")

    def * = (id, data)
  }
  val gpsData = TableQuery[GpsData]

  val setup = DBIO.seq(
    gpsData.schema.create,
    gpsData ++= testData.zipWithIndex map { case (res, idx) =>
      (idx, com.google.common.io.Resources.toByteArray(res))
    }

  )

  lazy val testData = Seq(
    getClass.getResource("/test01.fit"),
    getClass.getResource("/test02.fit"),
    getClass.getResource("/test03.fit"),
    getClass.getResource("/test04.fit"),
    getClass.getResource("/test05.fit"),
    getClass.getResource("/test06.fit"),
    getClass.getResource("/test07.fit"),
    getClass.getResource("/test08.fit")
  )

}
