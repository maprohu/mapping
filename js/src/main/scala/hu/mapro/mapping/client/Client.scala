package hu.mapro.mapping.client

import com.github.sjsf.leaflet._
import hu.mapro.mapping.Api

import scala.scalajs.js.JSApp
import scala.scalajs.js.annotation.JSExport
import autowire._
import org.scalajs.dom
import scala.scalajs.js.JSConverters._
import scala.scalajs.concurrent.JSExecutionContext.Implicits.queue

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
    println("hello2")

    val map = LMap("map").setView(LLatLng(38.723582, -9.166328), 14.0)
    LTileLayer(
      "http://{s}.tile.openstreetmap.org/{z}/{x}/{y}.png"
    ).addTo(map)


    Ajaxer[Api].wayTypes().call().foreach { types =>
      LControl().layers(


      ).addTo(map)
    }

    Ajaxer[Api].track().call().foreach { t =>
      LPolyline(
        t.positions.map(p => LLatLng(p.lat, p.lon)).toJSArray
      ).addTo(map)
    }

  }
}
