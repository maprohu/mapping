package hu.mapro.mapping

import scala.concurrent.Future

case class Position(lat: Double, lon: Double, timestamp: Long = 0)
case class Track(positions: Seq[Position], id : Long = 0)


trait Api {
  def cycleways() : Future[Seq[Track]]
  def tracks() : Future[Seq[Track]]
  def generateImg(bounds: Seq[Position]) : Future[Seq[Seq[Position]]]
}


sealed trait ServerToClientMessage
object Tick extends ServerToClientMessage
case class GpsTracksAdded(tracks: Seq[Track]) extends ServerToClientMessage
case class GpsTracksRemoved(tracks: Seq[Long]) extends ServerToClientMessage

sealed trait ClientToServerMessage
case class DeleteTrack() extends ClientToServerMessage


object pickle {
  def serverToClient(msg: ServerToClientMessage): String = upickle.default.write[ServerToClientMessage](msg)
  def serverToClient(msg: String): ServerToClientMessage = upickle.default.read[ServerToClientMessage](msg)

  def clientToServer(msg: ClientToServerMessage): String = upickle.default.write[ClientToServerMessage](msg)
  def clientToServer(msg: String): ClientToServerMessage = upickle.default.read[ClientToServerMessage](msg)
}
