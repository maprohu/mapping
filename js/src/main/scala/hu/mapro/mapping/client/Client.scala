package hu.mapro.mapping.client

import com.github.sjsf.leaflet._
import com.github.sjsf.leaflet.draw._
import hu.mapro.mapping.{Track, Api}

import scala.scalajs.js
import scala.scalajs.js.{UndefOr, JSApp}
import scala.scalajs.js.annotation.JSExport
import autowire._
import org.scalajs.dom
import scala.scalajs.js.JSConverters._
import scala.scalajs.concurrent.JSExecutionContext.Implicits.queue
import org.querki.jsext.JSOptionBuilder.builder2Options
import UndefOr._
import org.querki.jsext._

object Ajaxer extends autowire.Client[String, upickle.default.Reader, upickle.default.Writer]{
  override def doCall(req: Request) = {
    dom.ext.Ajax.post(
      url = "/ajax/" + req.path.mkString("/"),
      data = upickle.default.write(req.args)
    ).map(_.responseText)
  }

  def read[Result: upickle.default.Reader](p: String) = upickle.default.read[Result](p)
  def write[Result: upickle.default.Writer](r: Result) = upickle.default.write(r)
}

object Client extends JSApp {
  @JSExport
  override def main(): Unit = {
    println("hello7")

    val map = LMap("map").setView(LLatLng(38.723582, -9.166328), 14.0)
    val osmLayer = LTileLayer(
      "http://{s}.tile.openstreetmap.org/{z}/{x}/{y}.png"
    ).addTo(map)

    val layers = LControls.layers()
    layers.addTo(map)
    layers.addBaseLayer(osmLayer, "OSM")

    val drawLayer = LeafLeft.featureGroup()
    drawLayer.addTo(map)
    layers.addOverlay(drawLayer, "Drawings")

    val drawControl = new LControlDraw(
      LControlDrawOptions
        .edit(EditOptions.featureGroup(drawLayer))
        ._result
    )
    drawControl.addTo(map)

    map.on("draw:created", {
      e:LDrawCreatedEvent => drawLayer.addLayer(e.layer)
    })


    Ajaxer[Api].cycleways().call().foreach { tracks =>
      val layer = LMultiPolyline(
        tracks.map { track =>
          track.positions.map(p => LLatLng(p.lat, p.lon)).toJSArray
        }.toJSArray,
        LPolylineOptions.color("yellow")._result
      ).addTo(map)
      layers.addOverlay(layer, "Cycleways")


      Ajaxer[Api].tracks().call().foreach { tracks =>
        tracks.zipWithIndex.foreach { case (track, idx) =>
          val layer = LPolyline(
            track.positions.map(p => LLatLng(p.lat, p.lon)).toJSArray
          ).addTo(map)
          layers.addOverlay(layer, s"Track ${idx+1}")
        }
      }

    }
  }
}
