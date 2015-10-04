package hu.mapro.mapping.services

import slick.dbio.DBIO
import slick.jdbc.JdbcBackend

import slick.jdbc.{JdbcBackend, DatabaseUrlDataSource}
import slick.jdbc.JdbcBackend.Database
import slick.driver.PostgresDriver.api._
import slick.jdbc.meta.MTable
import scala.concurrent.Future

/**
 * Created by marci on 04-10-2015.
 */
trait BlobStore[I] {

  def read(id: I): Future[Array[Byte]]
  def write(ir: I, data: Array[Byte]): Future[Unit]

}

