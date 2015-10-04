package hu.mapro.mapping.util

import slick.jdbc.meta.MTable

import scala.concurrent.Future
import scala.async.Async.{async, await}
import slick.lifted._
import slick.jdbc.JdbcBackend._
import slick.lifted.Shape._

/**
 * Created by marci on 04-10-2015.
 */
object SlickUtil {

  def schema(
    db: DatabaseDef,
    tables: Seq[TableQuery[_ <: AbstractTable[_]]]
  ): Future[Unit] =
    async {
      val tablesMeta = await( db.run(MTable.getTables) )
      for (table <- tables) {
        if (!tablesMeta.exists(t => t.name.name == table.baseTableRow.tableName)) {
          await( db.run(table.shaped.value.) )
        }
      }
    }

}
