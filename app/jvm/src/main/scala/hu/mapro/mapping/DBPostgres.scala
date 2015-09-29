package hu.mapro.mapping

import java.security.MessageDigest
import scala.concurrent.ExecutionContext.Implicits.global

import com.google.common.io.ByteSource
import slick.jdbc.DatabaseUrlDataSource
import slick.jdbc.JdbcBackend.Database
import slick.driver.PostgresDriver.api._
import slick.jdbc.meta.MTable

import scala.concurrent.{Future, Await}
import scala.util.Properties

/**
 * Created by marci on 23-09-2015.
 */
class DBPostgres extends DB {

  def md5(buf: Array[Byte]): Array[Byte] = MessageDigest.getInstance("MD5").digest(buf)
  def hex(buf: Array[Byte]): String = buf.map("%02X" format _).mkString
  def hash(buf: Array[Byte]): String = hex(md5(buf))

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


  case class GpsTrack(
    id: Option[Int],
    hash: String,
    data: Array[Byte]
  ) {
    def this(data: Array[Byte]) =
      this(None, hash(data), data)
    def this(id: Int, data: Array[Byte]) =
      this(Some(id), hash(data), data)
  }


  class GpsTracks(tag: Tag) extends Table[GpsTrack](tag, "gps_tracks") {
    def id = column[Int]("id", O.PrimaryKey, O.AutoInc)
    def hash = column[String]("hash")
    def data = column[Array[Byte]]("data")

    def * = (id.?, hash, data) <> (GpsTrack.tupled, GpsTrack.unapply)
  }
  val gpsTracks = TableQuery[GpsTracks]



  lazy val allGpsTracks : Future[Seq[Track]] =
    db.run(gpsTracks.result)
      .map(tracks => tracks.map(track => Fit.parseGpsTrack(ByteSource.wrap(track.data), track.id.get)) )


  def saveGpsTrack(data: Array[Byte]): Future[Int] = {
    db.run(
      (gpsTracks returning gpsTracks.map(_.id)) += new GpsTrack(data)
    )
  }

  def deleteGpsTrack(id: Int) = db.run(
    gpsTracks.filter(_.id === id).delete
  )
}

object DBPostgres {
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

  def apply() : Future[DBPostgres] = {
    val dbp = new DBPostgres
    import dbp._

    val setup = DBIO.seq(
      gpsTracks.schema.create,
      gpsTracks ++= testData map { res =>
        val data = com.google.common.io.Resources.toByteArray(res)
        GpsTrack(
          None,
          hash(data),
          data
        )
      }
    )
    db
      .run(MTable.getTables)
      .flatMap[Any] {
        case tables if !tables.exists(t => t.name.name == "gps_tracks") => db.run(setup)
        case _ => Future(())
      }.map(_ => dbp)
  }
}