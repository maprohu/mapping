package hu.mapro.mapping

import akka.actor.ActorSystem
import hu.mapro.mapping.pages.Page
import spray.http.{HttpEntity, MediaTypes}
import spray.routing.SimpleRoutingApp

import scala.util.Properties

object App extends SimpleRoutingApp {
  def main(args: Array[String]): Unit = {
    implicit val system = ActorSystem()
    val port = Properties.envOrElse("PORT", "9080").toInt
    startServer("0.0.0.0", port = port){
      get{
        pathSingleSlash{
          complete{
            HttpEntity(
              MediaTypes.`text/html`,
              Page.skeleton.render
            )
          }
        } ~
        getFromResourceDirectory("")
      }
    }
  }
}
