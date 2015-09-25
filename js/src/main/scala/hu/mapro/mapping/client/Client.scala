package hu.mapro.mapping.client

import com.github.sjsf.leaflet._
import com.github.sjsf.leaflet.draw._
import hu.mapro.mapping.{Track, Api}
import upickle.Js
import upickle.default._

import scala.concurrent.Future
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

object Ajaxer extends autowire.Client[Js.Value, Reader, Writer]{
  override def doCall(req: Request) : Future[Js.Value] = {
    dom.ext.Ajax.post(
      url = "/ajax/" + req.path.mkString("/"),
      data = upickle.json.write(Js.Obj(req.args.toSeq:_*))
    )
      .map(_.responseText)
      .map(upickle.json.read)
  }

  def read[Result: Reader](p: Js.Value) = readJs[Result](p)
  def write[Result: Writer](r: Result) = writeJs(r)
}

object Client extends JSApp with MainClientModule {
  @JSExport
  override def main(): Unit = {
    println("hello7")

    ui.show

  }
}


