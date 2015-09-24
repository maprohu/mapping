package hu.mapro.mapping

import java.awt.Polygon

import akka.actor.ActorSystem
import com.google.common.io.ByteSource
import com.vividsolutions.jts.geom.{Coordinate, GeometryFactory}
import hu.mapro.mapping.fit.Fit
import hu.mapro.mapping.pages.Page
import spray.http.{HttpEntity, MediaTypes}
import spray.routing.SimpleRoutingApp
import upickle.Js
import upickle.default._
import scala.concurrent.ExecutionContext.Implicits.global
import scala.collection.JavaConversions._
import scala.concurrent.Future
import scala.util.Properties
import scala.xml.{XML, PrettyPrinter}
import DB._
import slick.driver.PostgresDriver.api._

object Router extends autowire.Server[Js.Value, Reader, Writer]{
  def read[Result: Reader](p: Js.Value) = upickle.default.readJs[Result](p)
  def write[Result: Writer](r: Result) = upickle.default.writeJs(r)
}

object App extends SimpleRoutingApp with Api {
  def main(args: Array[String]): Unit = {
    implicit val system = ActorSystem()
    val port = Properties.envOrElse("PORT", "9080").toInt
    startServer("0.0.0.0", port = port){
      get{
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
        path("ajax" / Segments){ s =>
          extract(_.request.entity.asString) { e =>
            complete {
              Router.route[Api](App)(
                autowire.Core.Request(
                  s,
                  upickle.json.read(e).asInstanceOf[Js.Obj].value.toMap
                )
              ).map(upickle.json.write)
            }
          }
        }
      }
    }
  }

  lazy val allGpsTracks : Future[Seq[Track]] =
    db.run(gpsTracks.result)
      .map(tracks => tracks.map(track => parseGpsTrack(ByteSource.wrap(track.data))) )

  override def tracks(): Future[Seq[Track]] = allGpsTracks

  def parseGpsTrack(resource: ByteSource): Track = {
    Track(
      Fit.readRecords(resource)
        .filter( r => r.getPositionLat !=null & r.getPositionLong != null)
        .map { r =>
          Position(semiToDeg(r.getPositionLat), semiToDeg(r.getPositionLong))
        }
    )
  }
  
  final def semiToDeg(semi : Int) : Double =
    semi * (180.0 / math.pow(2, 31) )

  lazy val cws: Seq[Track] = MS.cycleways(
    node => Position(
      lat = (node \ "@lat").text.toDouble,
      lon = (node \ "@lon").text.toDouble
    )
  )(
    (way, nodes) => Track(nodes)
  )

  override def cycleways(): Seq[Track] = {
    cws
  }

  def wayTypes(): Seq[String] = MS.highwayTags

  val GF = new GeometryFactory()

  override def generateImg(bounds: Seq[Position]): Future[Seq[Seq[Position]]] = {
    val polygon = GF.createPolygon(((bounds.last +: bounds) map {(pos:Position) => new Coordinate(pos.lat, pos.lon)}) .toArray)

    def isInside(p: Position) : Boolean = polygon.contains(GF.createPoint(new Coordinate(p.lat, p.lon)))

    def removeOuts(posIn : Iterator[(Position, Boolean)], acc: Seq[Seq[Position]]) : Seq[Seq[Position]] = {
      val (in, rest) = posIn dropWhile {!_._2} span {_._2}
      if (in.isEmpty) acc else removeOuts(rest, (in map {_._1}).toSeq +: acc)
    }

    allGpsTracks.map { tracks =>
      val result = tracks flatMap { track =>
        val positions = track.positions
        val posKeep = (false +: (positions map isInside) :+ false) sliding 3 map {_.exists{identity}}
        removeOuts( positions.iterator zip posKeep, Seq() )
      }

      XML.save("d:\\temp\\tracks.xml", OSM.xml(result, "cycleway"))

      result
    }
  }
}
