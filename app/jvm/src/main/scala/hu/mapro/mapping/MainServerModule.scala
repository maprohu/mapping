package hu.mapro.mapping

import akka.actor.ActorSystem
import com.softwaremill.macwire._
import akka.stream.ActorMaterializer
import hu.mapro.mapping.daemon.DaemonService

import scala.concurrent.Future

/**
 * Created by pappmar on 25/09/2015.
 */
class MainServerModule {
  implicit lazy val actorSystem = ActorSystem()
  implicit lazy val materializer = ActorMaterializer()
  lazy val db = wire[DBPostgres]
  lazy val service = wire[Service]
  lazy val mappingClients = wire[MappingClients]
  lazy val webservice = wire[Webservice]
  lazy val daemonService = wire[DaemonService]
}


