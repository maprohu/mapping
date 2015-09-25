package hu.mapro.mapping

import akka.actor.ActorSystem
import akka.stream.ActorMaterializer

import scala.concurrent.Future

/**
 * Created by pappmar on 25/09/2015.
 */
class MainServerModule {
  lazy implicit val actorSystem = ActorSystem()
  lazy implicit val materializer = ActorMaterializer()
  lazy implicit val db = new DBPostgres
  lazy implicit val service = new Service
  lazy implicit val webservice = new Webservice(this)
}

trait DB {

  def allGpsTracks : Future[Seq[Track]]

}

trait DI {
  def

}

object Actors extends Enumeration {
  val Main = Value
}
