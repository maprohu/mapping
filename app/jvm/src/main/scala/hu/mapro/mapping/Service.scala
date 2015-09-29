package hu.mapro.mapping


import com.vividsolutions.jts.geom.{Coordinate, GeometryFactory}
import hu.mapro.mapping.Messaging.Cycleways
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

class Service(db: DB) extends Api {





  lazy val cws: Cycleways = MS.cycleways(
    node => Coordinates(
      lat = (node \ "@lat").text.toDouble,
      lon = (node \ "@lon").text.toDouble
    )
  )(
    (way, nodes) => nodes
  )

  override def cycleways() = Future {
    cws
  }

  def wayTypes(): Seq[String] = MS.highwayTags

  val GF = new GeometryFactory()

  override def generateImg(bounds: Messaging.Polygon): Future[Seq[Seq[Position]]] = {
    val polygon = GF.createPolygon(((bounds.last +: bounds) map { pos => new Coordinate(pos.lat, pos.lon)}) .toArray)

    def isInside(p: Position) : Boolean = polygon.contains(GF.createPoint(new Coordinate(p.lat, p.lon)))

    def removeOuts(posIn : Iterator[(Position, Boolean)], acc: Seq[Seq[Position]]) : Seq[Seq[Position]] = {
      val (in, rest) = posIn dropWhile {!_._2} span {_._2}
      if (in.isEmpty) acc else removeOuts(rest, (in map {_._1}).toSeq +: acc)
    }

    db.allGpsTracks.map { tracks =>
      val result = tracks flatMap { track =>
        val positions = track._1.positions
        val posKeep = (false +: (positions map isInside) :+ false) sliding 3 map {_.exists{identity}}
        removeOuts( positions.iterator zip posKeep, Seq() )
      }

      //XML.save("d:\\temp\\tracks.xml", OSM.xml(result, "cycleway"))

      result
    }
  }


}




