package hu.mapro.mapping

case class Position(lat: Double, lon: Double)
case class Track(positions: Seq[Position])


trait Api {
  def track() : Track
}
