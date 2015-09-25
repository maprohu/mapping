package hu.mapro.mapping

import java.awt.Polygon

import akka.actor.ActorSystem
import akka.http.scaladsl._
import akka.http.scaladsl.model._
import akka.http.scaladsl.model.ws.{TextMessage, Message}
import akka.stream.ActorMaterializer
import akka.stream.scaladsl.{Source, Sink}
import com.google.common.io.ByteSource
import com.vividsolutions.jts.geom.{Coordinate, GeometryFactory}
import hu.mapro.mapping.fit.Fit
import hu.mapro.mapping.pages.Page
import upickle.Js
import upickle.default._
import scala.concurrent.ExecutionContext.Implicits.global
import scala.collection.JavaConversions._
import scala.concurrent.Future
import scala.util.Properties
import scala.xml.{XML, PrettyPrinter}
import DB._
import slick.driver.PostgresDriver.api._
import akka.http.scaladsl.server.Directives


trait Service extends Api {

  lazy val allGpsTracks : Future[Seq[Track]] =
    db.run(gpsTracks.result)
      .map(tracks => tracks.map(track => parseGpsTrack(ByteSource.wrap(track.data))) )

  override def tracks(): Future[Seq[Track]] = allGpsTracks

  def parseGpsTrack(resource: ByteSource): Track = {
    Track(
      Fit.readRecords(resource)
        .filter( r => r.getPositionLat !=null & r.getPositionLong != null)
        .map { r =>
          Position(
            semiToDeg(r.getPositionLat),
            semiToDeg(r.getPositionLong),
            r.getTimestamp.getTimestamp
          )
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

  override def cycleways() = Future {
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

      //XML.save("d:\\temp\\tracks.xml", OSM.xml(result, "cycleway"))

      result
    }
  }

  def newClient : Client = new Client

}

class Client {
  val sink : Sink[Message, Any] =
    Sink.foreach {
      case TextMessage.Strict(msg) => println(msg)
    }

  val source : Source[Message, Any] =
    Source.empty
}
