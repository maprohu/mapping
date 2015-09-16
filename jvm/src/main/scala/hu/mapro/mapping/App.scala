package hu.mapro.mapping

import akka.actor.ActorSystem
import hu.mapro.mapping.fit.Fit
import hu.mapro.mapping.pages.Page
import spray.http.{HttpEntity, MediaTypes}
import spray.routing.SimpleRoutingApp
import scala.concurrent.ExecutionContext.Implicits.global

import scala.util.Properties

object Router extends autowire.Server[String, upickle.default.Reader, upickle.default.Writer]{
  def read[Result: upickle.default.Reader](p: String) = upickle.default.read[Result](p)
  def write[Result: upickle.default.Writer](r: Result) = upickle.default.write(r)
}

object App extends SimpleRoutingApp with Api {
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
      } ~
      post {
        path("ajax" / Segments){ s =>
          extract(_.request.entity.asString) { e =>
            complete {
              Router.route[Api](App)(
                autowire.Core.Request(
                  s,
                  upickle.default.read[Map[String, String]](e)
                )
              )
            }
          }
        }
      }
    }
  }

  override def track(): Track = {
    Track(
      Fit.readRecords(getClass.getResource("/test01.fit")).map { r =>
        Position(semiToDeg(r.getPositionLat), semiToDeg(r.getPositionLong))
      }
    )
  }

  final def semiToDeg(semi : Int) : Double =
    semi * (180.0 / math.pow(2, 31) )
}
