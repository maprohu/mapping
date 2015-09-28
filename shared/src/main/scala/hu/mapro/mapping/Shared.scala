package hu.mapro.mapping

import hu.mapro.mapping.Messaging.{Polygon, Cycleways}

import scala.concurrent.Future

trait LatLon {
  val lat : Double
  val lon : Double
}

case class Coordinates(lat: Double, lon: Double) extends LatLon
case class Position(lat: Double, lon: Double, timestamp: Long) extends LatLon
case class Track(positions: Seq[Position], id : Int)



trait Api {
  def cycleways() : Future[Cycleways]
  def tracks() : Future[Seq[Track]]
  def generateImg(bounds: Polygon) : Future[Seq[Seq[Position]]]
}


object Messaging {
  type Polygon = Seq[Coordinates]
  type Polyline = Seq[Coordinates]
  type Cycleways = Seq[Polyline]
  type Polygons = Seq[Polygon]

  sealed trait ServerToClientMessage
  object Tick extends ServerToClientMessage
  case class GpsTracksAdded(tracks: Seq[Track]) extends ServerToClientMessage
  case class GpsTracksRemoved(tracks: Seq[Long]) extends ServerToClientMessage
  case class CyclewaysChanged(cycleways: Cycleways) extends ServerToClientMessage

  sealed trait ClientToServerMessage
  case class DeleteTrack() extends ClientToServerMessage
  case class FetchCycleways(polygon: Polygon) extends ClientToServerMessage
}


object pickle {
  import Messaging._
  def serverToClient(msg: ServerToClientMessage): String = upickle.default.write[ServerToClientMessage](msg)
  def serverToClient(msg: String): ServerToClientMessage = upickle.default.read[ServerToClientMessage](msg)

  def clientToServer(msg: ClientToServerMessage): String = upickle.default.write[ClientToServerMessage](msg)
  def clientToServer(msg: String): ClientToServerMessage = upickle.default.read[ClientToServerMessage](msg)
}
