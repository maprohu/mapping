package hu.mapro.mapping

import scala.concurrent.Future

case class Position(lat: Double, lon: Double)
case class Track(positions: Seq[Position])


trait Api {
  def cycleways() : Seq[Track]
  def tracks() : Future[Seq[Track]]
  def generateImg(bounds: Seq[Position]) : Future[Seq[Seq[Position]]]
}
