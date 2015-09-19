package hu.mapro.mapping.client

import com.github.sjsf.leaflet._
import hu.mapro.mapping.Api

import scala.scalajs.js.JSApp
import scala.scalajs.js.annotation.JSExport
import autowire._
import org.scalajs.dom
import scala.scalajs.js.JSConverters._
import scala.scalajs.concurrent.JSExecutionContext.Implicits.queue
import org.querki.jsext.JSOptionBuilder.builder2Options

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
    LTileLayer(
      "http://{s}.tile.openstreetmap.org/{z}/{x}/{y}.png"
    ).addTo(map)

    Ajaxer[Api].tracks().call().foreach { tracks =>
      tracks.foreach { track =>
        LPolyline(
          track.positions.map(p => LLatLng(p.lat, p.lon)).toJSArray
        ).addTo(map)
      }
    }

    Ajaxer[Api].cycleways().call().foreach { tracks =>
      LMultiPolyline(
        tracks.map { track =>
          track.positions.map(p => LLatLng(p.lat, p.lon)).toJSArray
        }.toJSArray,
        LPolylineOptions.color("yellow")._result
      ).addTo(map)
    }
  }
}
