package hu.mapro.mapping.client

import autowire._
import com.github.sjsf.leaflet._
import com.github.sjsf.leaflet.contextmenu.Implicits._
import com.github.sjsf.leaflet.contextmenu.{MixinItemOptions, MixinOptions}
import com.github.sjsf.leaflet.draw._
import com.github.sjsf.leaflet.sidebarv2.{Html, LControlSidebar, Tab, TabLike}
import hu.mapro.mapping._
import hu.mapro.mapping.client.ui.GpsTracksUI
import monifu.reactive.Ack.Continue
import monifu.reactive.Observer
import org.querki.jsext.JSOptionBuilder._
import org.scalajs.dom
import org.scalajs.dom.raw.{MessageEvent, WebSocket, FormData}
import org.scalajs.dom._
import hu.mapro.mapping.Coordinates
import hu.mapro.mapping.Messaging._
import rx._

import monifu.concurrent.Implicits.globalScheduler
import scala.concurrent.Future
import scala.scalajs.js
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
  val socket = new WebSocket(getWebsocketUri(dom.document))

  val clientToServer = new Observer[ClientToServerMessage] {
    def onNext(elem: ClientToServerMessage) = Future { toServer(elem); Continue }

    def onError(ex: Throwable) = {}

    def onComplete() = {}
  }

  val body = dom.document.body


  val gpsTracksTab = new GpsTracksUI(clientToServer)
  val html = Html
    .generate(
      Seq(
        gpsTracksTab.tab,
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
            val poss: Seq[Coordinates] = p.getLatLngs().toSeq.map(ll => Coordinates(ll.lat, ll.lng))
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
      poly.map { pos : Coordinates => LLatLng(pos.lat, pos.lon) }.toJSArray
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
            ll => Coordinates(ll.lat, ll.lng)
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

  gpsTracksTab.gpsTracksLayer.addTo(map)
  layers.addOverlay(gpsTracksTab.gpsTracksLayer, "GPS Tracks")


  socket.onopen = { (event:Event) =>
    println("websocket opened")
  }
  socket.onmessage = { (event:MessageEvent) =>

    val msg = pickle.serverToClient(event.data.toString)

    msg match {
      case m @ GpsTracksAdded(tracks) =>
        gpsTracksTab.serverToClient.onNext(m)
      case m @ GpsTracksRemoved(tracks) =>
        gpsTracksTab.serverToClient.onNext(m)
      case CyclewaysChanged(cycleways) =>
        cyclewaysLayer.setLatLngs(
          cycleways.map { track =>
            track.map(p => LLatLng(p.lat, p.lon)).toJSArray
          }.toJSArray
        )

      case Tick => println("tick")
      case _ => println("wtf?")
    }
  }
  socket.onerror = { (event:ErrorEvent) =>
    println(s"websocket error: $event")
  }
  socket.onclose = { (event:CloseEvent) =>
    println(s"websocket closed: $event")
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
    def visibleWhen(rx: Rx[Boolean]) = custom(elem => Obs(rx) { jQuery(elem).toggleClass("hidden", !rx())})
    def hiddenWhen(rx: Rx[Boolean]) = custom(elem => Obs(rx) { jQuery(elem).toggleClass("hidden", rx())})
    def jquery = jQuery(tag.render)
    def jquery(f: JQuery => Unit) = custom(elem => f(jQuery(elem)))
    def click(f: () => Unit) = jquery(_.click((data:JQueryEventObject) => f()))
  }

  def toPolyLine(track: Track) : LPolyline = LPolyline(
    track.positions.map(p => LLatLng(p.lat, p.lon)).toJSArray
  )
}



