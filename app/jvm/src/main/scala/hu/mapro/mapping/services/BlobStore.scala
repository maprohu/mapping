package hu.mapro.mapping.services

import slick.driver.PostgresDriver
import slick.jdbc.JdbcBackend._

import slick.driver.PostgresDriver.api._
import slick.jdbc.meta.MTable
import slick.lifted
import scala.concurrent.Future

/**
 * Created by marci on 04-10-2015.
 */
trait BlobStore[I] {

  def read(id: I): Future[Option[Array[Byte]]]
  def write(ir: I, data: Array[Byte]): Future[Unit]

}


class DBBlobStore(
  db: DatabaseDef
) extends BlobStore[String] {


  class Blobs(tag: Tag) extends Table[(String, Array[Byte])](tag, "blobs") {
    def id = column[String]("id", O.PrimaryKey)
    def data = column[Array[Byte]]("data")
    def * = (id, data)
  }
  val blobs:lifted.TableQuery[Blobs] = TableQuery[Blobs]

  blobs.baseTableRow.tableName
  private val schema: PostgresDriver.DDL = blobs.schema
  db.run(schema.create)

  def read(id: String) = db.run(blobs.filter(_.id === id).map(_.data).result.headOption)

  def write(ir: String, data: Array[Byte]) = db.run(blobs.insertOrUpdate((ir, data))).map(_=>())
}