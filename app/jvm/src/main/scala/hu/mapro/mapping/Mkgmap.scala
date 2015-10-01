package hu.mapro.mapping

import java.io.File
import java.nio.file.Files

import com.google.common.io.Resources
import com.vividsolutions.jts.geom.{Coordinate, GeometryFactory}

import scala.concurrent.Future
import scala.xml.XML

/**
 * Created by marci on 23-09-2015.
 */
object Mkgmap {

  val GF = new GeometryFactory()

  override def generateImg(tracks: Seq[Track], bounds: Messaging.Polygon): Future[Seq[Seq[Position]]] = {
    val polygon = GF.createPolygon(((bounds.last +: bounds) map { pos => new Coordinate(pos.lat, pos.lon)}) .toArray)

    def isInside(p: Position) : Boolean = polygon.contains(GF.createPoint(new Coordinate(p.lat, p.lon)))

    def removeOuts(posIn : Iterator[(Position, Boolean)], acc: Seq[Seq[Position]]) : Seq[Seq[Position]] = {
      val (in, rest) = posIn dropWhile {!_._2} span {_._2}
      if (in.isEmpty) acc else removeOuts(rest, (in map {_._1}).toSeq +: acc)
    }

    val result = tracks flatMap { track =>
      val positions = track.positions
      val posKeep = (false +: (positions map isInside) :+ false) sliding 3 map {_.exists{identity}}
      removeOuts( positions.iterator zip posKeep, Seq() )
    }

    val tmpOsmXml = File.createTempFile("img", "osm")
    XML.save(tmpOsmXml.getAbsolutePath, OSM.xml(result, "cycleway"))

//    val fos = new FileOutputStream(tmpOsmXml)
//    val wr = Channels.newWriter(fos.getChannel, "UTF-8")
//    ultimately(wr.close())(
//      XML.write(
//        wr,
//        OSM.xml(result, "cycleway"),
//        "UTF-8",
//        true,
//        null
//      )
//    )

    val tmpOsmOut = Files.createTempDirectory("osmOut")

    val tmpStyle = File.createTempFile("imgstyle", "zip")

    Resources.
    com.google.common.io.Files.asByteSink(tmpStyle)


    uk.me.parabola.mkgmap.main.Main.mainNoSystemExit(
      Seq(
        s"--output-dir=${tmpOsmOut.toFile.getAbsolutePath}",
        (baseDirectory.value / "src" / "script" / "garmin" / "typ" / "mapstyle.txt").absolutePath
      ).toArray
    )
      //XML.save("d:\\temp\\tracks.xml", OSM.xml(result, "cycleway"))

      result
  }

}
