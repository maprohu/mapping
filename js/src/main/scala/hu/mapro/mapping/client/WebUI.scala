package hu.mapro.mapping.client

import autowire._
import com.github.sjsf.bootstrapnotify.notification
import com.github.sjsf.leaflet._
import com.github.sjsf.leaflet.contextmenu.Implicits._
import com.github.sjsf.leaflet.contextmenu.{MixinItemOptions, MixinOptions}
import com.github.sjsf.leaflet.draw._
import com.github.sjsf.leaflet.sidebarv2.{Html, LControlSidebar, Tab, TabLike}
import hu.mapro.mapping.Messaging._
import hu.mapro.mapping._
import org.querki.jsext.JSOptionBuilder._
import org.scalajs.dom
import org.scalajs.dom.raw.{MessageEvent, WebSocket, FormData}
import org.scalajs.dom.{Document, Element, Event}
import rx._

import scala.async.Async._
import scala.scalajs.concurrent.JSExecutionContext.Implicits.queue
import scala.scalajs.js
import scala.scalajs.js._
import scala.scalajs.js.Any._
import scala.scalajs.js.JSConverters._
import scala.scalajs.js.UndefOr._
import scalatags.JsDom.TypedTag
import scalatags.JsDom.all._
import org.scalajs.jquery._

/**
 * Created by pappmar on 21/09/2015.
 */
class WebUI(store: Store) extends UI {
  override def show: Unit = {
    new WebUIDom(store)
  }
}

class WebUIDom(store: Store) {
  val body = dom.document.body

  val html = Html
    .generate(
      Seq(
        new GpsTracksTab,
        Tab(
          id = "database",
          icon = Seq(
            i(cls := "fa fa-database")
          ),
          panel = Seq(
          )
        )
      )
    )

  html.foreach(node => body.appendChild(node.render))

  val map = LMap(
    "map",
    LMapOptions
    .contextmenu(true)
    ._result
  ).setView(LLatLng(38.723582, -9.166328), 14.0)
  val osmLayer = LTileLayer(
    "http://{s}.tile.openstreetmap.org/{z}/{x}/{y}.png",
    LTileLayerOptions.maxZoom(19)._result
  ).addTo(map)

  val layers = LControls.layers()
  layers.addTo(map)
  layers.addBaseLayer(osmLayer, "OSM")

  LControlSidebar("sidebar").addTo(map)

  val featureOptions = (p: LPolygon) => MixinOptions
    .contextmenu(true)
    .contextmenuInheritItems(false)
    .contextmenuItems(
      MixinItemOptions
        .text("Fetch OSM Cycleways")
        .callback { (_:LLatLng, _:LPoint, _:LPoint) =>
          val poss: Seq[Coordinates] = p.getLatLngs().toSeq.map(ll => Coordinates(ll.lat, ll.lng))
          toServer(FetchCycleways(poss))
        }
      ,
      MixinItemOptions
        .text("Generate IMG")
        .callback {
          (_:LLatLng, _:LPoint, _:LPoint) =>
            val poss: Seq[Position] = p.getLatLngs().toSeq.map(ll => Position(ll.lat, ll.lng))
            println(poss)
            Ajaxer[Api].generateImg(poss).call().foreach { tracks =>
              LMultiPolyline(
                (tracks map { track =>
                  (track map { pos =>
                    LLatLng(pos.lat, pos.lon)
                  }).toJSArray
                }).toJSArray,
                LPolylineOptions.color("red")._result
              ).addTo(map)
            }
            ()
        }
    )
    ._result

  val drawLayer = LFeatureGroup()
  drawLayer.addTo(map)
  layers.addOverlay(drawLayer, "Drawings")
  for {
    drawings <- store.loadDrawings
    poly <- drawings
  } {
    val p = LPolygon(
      poly.map { pos : Position => LLatLng(pos.lat, pos.lon) }.toJSArray
    )
    p.bindContextMenu(featureOptions(p))
    drawLayer.addLayer(p)
  }

  val drawControl = new LControlDraw(
    LControlDrawOptions
      .draw(
        DrawOptions
          .circle(false)
          .marker(false)
          .polyline(false)
          .rectangle(false)
      )
      .edit(EditOptions.featureGroup(drawLayer))
      ._result
  )
  drawControl.addTo(map)

  val save = () => {
    store.saveDrawings(
      drawLayer.getLayers().toSeq.map {
        layer =>
          layer.asInstanceOf[LPolygon].getLatLngs().toSeq.map {
            ll => Position(ll.lat, ll.lng)
          }
      }
    )
  }
  map.on("draw:created", {
    e:LDrawCreatedEvent =>
      drawLayer.addLayer(e.layer)
      e.layer.bindContextMenu(featureOptions(e.layer.asInstanceOf[LPolygon]))
      save()
  })

  map.on("draw:edited", {
    e:LDrawEditedEvent =>
      save()
  })

  map.on("draw:deleted", {
    e:LDrawDeletedEvent =>
      save()
  })

  val cyclewaysLayer = LMultiPolyline(
    js.Array(),
    LPolylineOptions.color("Indigo")._result
  ).addTo(map)
  layers.addOverlay(cyclewaysLayer, "Cycleways")

//  for {
//    cycleways <- Ajaxer[Api].cycleways().call()
//  } {
//    cyclewaysLayer.setLatLngs(
//      cycleways.map { track =>
//        track.positions.map(p => LLatLng(p.lat, p.lon)).toJSArray
//      }.toJSArray
//    )
//  }

  val gpsTracksLayer = LMultiPolyline(
    js.Array(),
    LPolylineOptions.color("pink")._result
  ).addTo(map)
  layers.addOverlay(gpsTracksLayer, "GPS Tracks")

//    async {
//      val tracks = await { Ajaxer[Api].tracks().call() }
//      tracks.zipWithIndex.foreach { case (track, idx) =>
//        val layer = LPolyline(
//          track.positions.map(p => LLatLng(p.lat, p.lon)).toJSArray
//        ).addTo(map)
//        layers.addOverlay(layer, s"Track ${idx+1}")
//    }


  val socket = new WebSocket(getWebsocketUri(dom.document))

  socket.onopen = { (event:Event) =>
    println("opened")
    event
  }
  socket.onmessage = { (event:MessageEvent) =>
    val msg = pickle.serverToClient(event.data.toString)

    msg match {
      case GpsTracksAdded(tracks) =>
        for (track <- tracks) {
          gpsTracksLayer.addLayer(
            Util
              .toPolyLine(track)
              .setStyle(LPolylineOptions.color("red")._result)
          )
        }
      case CyclewaysChanged(cycleways) =>
        cyclewaysLayer.setLatLngs(
          cycleways.map { track =>
            track.map(p => LLatLng(p.lat, p.lon)).toJSArray
          }.toJSArray
        )

      case Tick => println("tick")
      case _ =>
    }
  }

  def toServer(msg: ClientToServerMessage): Unit = {
    socket.send(pickle.clientToServer(msg))
  }

  def getWebsocketUri(document: Document): String = {
    val wsProtocol = if (dom.document.location.protocol == "https:") "wss" else "ws"
    s"$wsProtocol://${dom.document.location.host}/socket"
  }
}

object Util {
  def custom[Builder](f: Element => Unit) : Modifier =
    new Modifier {
      override def applyTo(t: Element): Unit = f(t)
    }

  implicit class CustomTag[T <: Element](tag: TypedTag[T]) {
    def custom(f: T => Unit) : TypedTag[T] = tag.apply(Util.custom(elem => f(elem.asInstanceOf[T])))
  }

  def toPolyLine(track: Track) : LPolyline = LPolyline(
    track.positions.map(p => LLatLng(p.lat, p.lon)).toJSArray
  )
}

import hu.mapro.mapping.client.Util._

class GpsTracksTab extends TabLike {
  val id = "gpsTracks"
  val icon = Seq(
    i(cls := "fa fa-location-arrow")
  )
  val selectedFile = Var("")
  val panel = Seq(
    h2("GPS Tracks"),
    form(
      action := "upload/gps-track",
      div(
        cls := "form-group",
        label("Upload"),
        input(
          `type` := "file"
        ).custom { input =>
          input.onchange = { _:Event =>
            val formData = new FormData()
            jQuery.each(input.files, { (key:js.Any, value:js.Any) =>
              formData.append(key, value)
              val s = (new js.Object).asInstanceOf[JQueryAjaxSettings]
              s.url = "upload/gps-data"
              s.`type` = "POST"
              s.data = formData
              s.cache = false
              s.dataType = "json"
              s.processData = false
              s.contentType = false
              jQuery.ajax(s)

              notification("hello")

              ().asInstanceOf[js.Any]
            })


          }
        }
      )
    ).custom { form =>
      Obs(selectedFile) {
        selectedFile() match {
          case null | "" =>
          case _ => form.submit()
        }
      }
    }


  )
}
