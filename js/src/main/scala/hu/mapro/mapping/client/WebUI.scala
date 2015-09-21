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
import org.querki.jsext.JSOptionBuilder._
import UndefOr._
import org.querki.jsext._
import async.Async._
/**
 * Created by pappmar on 21/09/2015.
 */
class WebUI(store: Store) extends UI {
  override def show: Unit = {
    val map = LMap("map").setView(LLatLng(38.723582, -9.166328), 14.0)
    val osmLayer = LTileLayer(
      "http://{s}.tile.openstreetmap.org/{z}/{x}/{y}.png",
      LTileLayerOptions.maxZoom(19)._result
    ).addTo(map)

    val layers = LControls.layers()
    layers.addTo(map)
    layers.addBaseLayer(osmLayer, "OSM")

    async {
      val drawLayer = await { store.loadDrawings }
      drawLayer.addTo(map)
      layers.addOverlay(drawLayer, "Drawings")

      val drawControl = new LControlDraw(
        LControlDrawOptions
          .edit(EditOptions.featureGroup(drawLayer))
          ._result
      )
      drawControl.addTo(map)

      map.on("draw:created", {
        e:LDrawCreatedEvent =>
          drawLayer.addLayer(e.layer)
          store.saveDrawings(drawLayer)
      })

      map.on("draw:edited", {
        e:LDrawEditedEvent =>
          store.saveDrawings(drawLayer)
      })

      map.on("draw:deleted", {
        e:LDrawDeletedEvent =>
          store.saveDrawings(drawLayer)
      })

      val cycleways = await { Ajaxer[Api].cycleways().call() }

      val cyclewaysLayer = LMultiPolyline(
        cycleways.map { track =>
          track.positions.map(p => LLatLng(p.lat, p.lon)).toJSArray
        }.toJSArray,
        LPolylineOptions.color("yellow")._result
      ).addTo(map)
      layers.addOverlay(cyclewaysLayer, "Cycleways")

      val tracks = await { Ajaxer[Api].tracks().call() }

      tracks.zipWithIndex.foreach { case (track, idx) =>
        val layer = LPolyline(
          track.positions.map(p => LLatLng(p.lat, p.lon)).toJSArray
        ).addTo(map)
        layers.addOverlay(layer, s"Track ${idx+1}")
      }

    }

  }
}
