package hu.mapro.mapping

import slick.jdbc.DatabaseUrlDataSource
import slick.jdbc.JdbcBackend.Database
import slick.driver.PostgresDriver.api._
import slick.jdbc.meta.MTable
import scala.concurrent.ExecutionContext.Implicits.global

import scala.concurrent.Await
import scala.util.Properties

/**
 * Created by marci on 23-09-2015.
 */
object DB {

  val urlString = Properties.envOrElse("DATABASE_URL", "postgres://mapping:mapping@localhost/mapping")

  val ds = new DatabaseUrlDataSource
  ds.setUrl(urlString)
  ds.setDriver("org.postgresql.Driver")
//  ds.setDriver("slick.driver.PostgresDriver$")

//  val dbUri = new URI(urlString)
//
//  val username = dbUri.getUserInfo().split(":")(0)
//  val password = dbUri.getUserInfo().split(":")(1)
//  val dbUrl = s"jdbc:postgresql://${dbUri.getHost()}:${dbUri.getPort()}${dbUri.getPath()}"
//
//  val db = Database.forURL(
//    url = dbUrl,
//    user = username,
//    password = password,
//    driver = "org.postgresql.Driver"
//  )

  val db = Database.forDataSource(ds)


  case class GpsTrack(id: Option[Int], data: Array[Byte])
  class GpsTracks(tag: Tag) extends Table[GpsTrack](tag, "gps_tracks") {
    def id = column[Int]("id", O.PrimaryKey, O.AutoInc)
    def data = column[Array[Byte]]("data")

    def * = (id.?, data) <> (GpsTrack.tupled, GpsTrack.unapply)
  }
  val gpsTracks = TableQuery[GpsTracks]

  val setup = DBIO.seq(
    gpsTracks.schema.create,
    gpsTracks ++= testData map {res => GpsTrack(None, com.google.common.io.Resources.toByteArray(res))}
  )
  db.run(MTable.getTables).onSuccess { case tables => if (!tables.exists(t => t.name.name == "gps_tracks")) db.run(setup) }

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
