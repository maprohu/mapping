package hu.mapro.mapping

import java.awt.Polygon

import akka.actor.ActorSystem
import akka.http.scaladsl._
import akka.http.scaladsl.model._
import akka.http.scaladsl.model.ws._
import akka.stream.ActorMaterializer
import akka.stream.javadsl.FlattenStrategy
import akka.stream.scaladsl.{Sink, Source, Flow}
import akka.util.ByteString
import com.google.common.io.ByteSource
import com.vividsolutions.jts.geom.{Coordinate, GeometryFactory}
import hu.mapro.mapping.actors.MainActor.{UploadComplete, GpsTrackUploaded}
import hu.mapro.mapping.pages.Page
import upickle.Js
import upickle.default._
import scala.concurrent.ExecutionContext.Implicits.global
import scala.collection.JavaConversions._
import scala.concurrent.Future
import scala.util.Properties
import scala.xml.{XML, PrettyPrinter}
import slick.driver.PostgresDriver.api._
import akka.http.scaladsl.server.{ExpectedWebsocketRequestRejection, Directives}

object Router extends autowire.Server[Js.Value, Reader, Writer]{
  def read[Result: Reader](p: Js.Value) = upickle.default.readJs[Result](p)
  def write[Result: Writer](r: Result) = upickle.default.writeJs(r)
}

object App extends MainServerModule with Directives {
  def main(args: Array[String]): Unit = {

    val port = Properties.envOrElse("PORT", "9080").toInt
    val route = {
      get {
        webservice.route ~
        daemonService.route ~
        pathSingleSlash{
          complete {
            HttpEntity(
              MediaTypes.`text/html`,
              Page.full.render
            )
          }
        } ~
        path( "index-dev.html" ) {
          complete {
            HttpEntity(
              MediaTypes.`text/html`,
              Page.fast.render
            )
          }
        } ~
        getFromResourceDirectory("public")
      } ~
      post {
        path("ajax" / Segments) { s =>
          entity(as[String]) { e =>
            complete {
              Router.route[Api](service)(
                autowire.Core.Request(
                  s,
                  upickle.json.read(e).asInstanceOf[Js.Obj].value.toMap
                )
              ).map(upickle.json.write)
            }
          }
        } ~
        path("upload" / Segments) { s =>
          entity(as[Multipart.FormData]) { formData =>
            complete {
              formData.parts
                .mapAsync(1)( _.entity.dataBytes.runFold(ByteString.empty)(_ ++ _) )
                .map( bytes => GpsTrackUploaded(bytes.toArray) )
                .runWith(Sink.actorRef(mappingClients.mainActor, UploadComplete))
              ""
            }
          }
        }
      }
    }
    Http().bindAndHandle(
      handler = route,
      interface = "0.0.0.0",
      port = port
    )
  }





}
