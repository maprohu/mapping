package hu.mapro.mapping

/**
 * Created by marci on 26-09-2015.
 */
object Geo {

  case class Bounds(minlat : Double, minlon : Double, maxlat : Double, maxlon : Double)

  // FIXME polygon passing antimeridian?
  def bounds(points: Seq[LatLon]) = Bounds(
    minlat = points.map(_.lat).min,
    minlon = points.map(_.lon).min,
    maxlat = points.map(_.lat).max,
    maxlon = points.map(_.lon).max
  )


}
