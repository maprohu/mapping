package hu.mapro.mapping

import java.awt.Polygon

import akka.actor.Actor.Receive
import akka.actor.{Props, ActorSystem}
import akka.http.scaladsl._
import akka.http.scaladsl.model._
import akka.http.scaladsl.model.ws.{TextMessage, Message}
import akka.stream.ActorMaterializer
import akka.stream.actor.ActorPublisher
import akka.stream.scaladsl.{Flow, Source, Sink}
import com.google.common.io.ByteSource
import com.vividsolutions.jts.geom.{Coordinate, GeometryFactory}
import hu.mapro.mapping.pages.Page
import upickle.Js
import upickle.default._
import scala.concurrent.ExecutionContext.Implicits.global
import scala.collection.JavaConversions._
import scala.concurrent.Future
import scala.util.Properties
import scala.xml.{XML, PrettyPrinter}
import slick.driver.PostgresDriver.api._
import akka.http.scaladsl.server.Directives


class Service(implicit db: DB) extends Api {


  override def tracks(): Future[Seq[Track]] = db.allGpsTracks



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

    db.allGpsTracks.map { tracks =>
      val result = tracks flatMap { track =>
        val positions = track.positions
        val posKeep = (false +: (positions map isInside) :+ false) sliding 3 map {_.exists{identity}}
        removeOuts( positions.iterator zip posKeep, Seq() )
      }

      //XML.save("d:\\temp\\tracks.xml", OSM.xml(result, "cycleway"))

      result
    }
  }


}




