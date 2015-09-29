package hu.mapro.mapping.daemon

import java.util.Date

import akka.actor.ActorSystem
import akka.http.scaladsl.model.ws.{Message, TextMessage}
import akka.http.scaladsl.server.Directives
import akka.stream.Materializer
import akka.stream.scaladsl.Flow
import akka.stream.stage._
import hu.mapro.mapping.Messaging._
import hu.mapro.mapping.actors.MainActor.ToAllClients

import scala.concurrent.duration._
/**
 * Created by pappmar on 29/09/2015.
 */
class DaemonService extends Directives {

  def route =
    path("daemon") {
      handleWebsocketMessages(websocketClientFlow())
    }


}
