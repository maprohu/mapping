package hu.mapro.mapping

import scala.concurrent.Future

case class Position(lat: Double, lon: Double, timestamp: Long = 0)
case class Track(positions: Seq[Position])


trait Api {
  def cycleways() : Future[Seq[Track]]
  def tracks() : Future[Seq[Track]]
  def generateImg(bounds: Seq[Position]) : Future[Seq[Seq[Position]]]
}


sealed trait Msg

