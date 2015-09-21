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

object Client extends JSApp with MainModule {
  @JSExport
  override def main(): Unit = {
    println("hello7")

    ui.show

  }
}


