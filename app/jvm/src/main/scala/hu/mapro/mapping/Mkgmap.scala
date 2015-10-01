package hu.mapro.mapping

import java.io.File
import java.nio.file.Files

import akka.actor.ActorSystem
import akka.event.Logging
import com.google.common.io.{ByteSource, Resources}
import com.vividsolutions.jts.geom.{Coordinate, GeometryFactory}

import scala.concurrent.Future
import scala.util.Failure
import scala.xml.XML

/**
 * Created by marci on 23-09-2015.
 */
class Mkgmap(actorSystem: ActorSystem) {

  import actorSystem.dispatcher

  var log = Logging(actorSystem, "mkgmap")

  val GF = new GeometryFactory()

  def generateImg(tracks: Seq[Track], bounds: Messaging.Polygon): Future[Option[ByteSource]] = Future({
    log.debug("Generating IMG...")
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

    if (result.flatten.isEmpty) {
      log.info("Nothing to be displayed on generated map")
      None
    }
    else {
      log.debug("creating tmp input files for mkgmap")
      log.debug("mkgmap: osm")
      val tmpOsmXml = File.createTempFile("img", ".osm")
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

      log.debug("mkgmap: style")
      val tmpStyle = File.createTempFile("imgstyle", ".zip")
      Resources.asByteSource(Resources.getResource("mapstyle.zip"))
        .copyTo(com.google.common.io.Files.asByteSink(tmpStyle))

      log.debug("mkgmap: typ")
      val tmpTyp = File.createTempFile("imgtyp", ".typ")
      Resources.asByteSource(Resources.getResource("mapstyle.typ"))
        .copyTo(com.google.common.io.Files.asByteSink(tmpTyp))

      val tmpOsmOut = Files.createTempDirectory("osmOut")

      val mkgparams = Seq(
        s"--output-dir=${tmpOsmOut.toFile.getAbsolutePath}",
        s"--style-file=${tmpStyle.getAbsolutePath}",
        "--style=cycling",
        "--gmapsupp",
        "--remove-ovm-work-files",
        "--transparent",
        s"--input-file=${tmpOsmXml.getAbsolutePath}",
        s"--input-file=${tmpTyp.getAbsolutePath}"
      )
      log.info("Running mkgmap with parameters: {}", mkgparams)
      uk.me.parabola.mkgmap.main.Main.mainNoSystemExit(
        mkgparams.toArray
      )

      tmpStyle.delete()
      tmpTyp.delete()
      tmpOsmXml.delete()

      val b =
        com.google.common.io.Files.toByteArray(
          tmpOsmOut.resolve("gmapsupp.img").toFile
        )

      //    Files.walkFileTree(tmpOsmOut, new SimpleFileVisitor[Path] {
      //      override def visitFile(file: Path, attrs: BasicFileAttributes): FileVisitResult = {
      //        Files.deleteIfExists(file)
      //        super.visitFile(file, attrs)
      //      }
      //    })
      Some(ByteSource.wrap(b))

    }

  }).andThen{case Failure(ex) => log.error(ex, "error generating img")}

}
