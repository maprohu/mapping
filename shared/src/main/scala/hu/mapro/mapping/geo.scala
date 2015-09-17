package hu.mapro.mapping

case class Position(lat: Double, lon: Double)
case class Track(positions: Seq[Position])


trait Api {
  def wayTypes() : Seq[String]
  def track() : Track
}
