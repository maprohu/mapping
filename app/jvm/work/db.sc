import hu.mapro.mapping.DBPostgres._
import scala.concurrent.duration._
import scala.concurrent.Await

val result = Await.result(db.run(setup), 5 seconds)