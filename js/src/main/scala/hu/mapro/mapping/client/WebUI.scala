package hu.mapro.mapping.client

import com.github.sjsf.leaflet._
import scalatags.JsDom.all._
import com.github.sjsf.leaflet.contextmenu.{FeatureMethods, MixinOptions, MixinItemOptions, ItemOptions}
import com.github.sjsf.leaflet.draw._
import com.github.sjsf.leaflet.sidebarv2.{Tab, Html, LControlSidebar}
import hu.mapro.mapping.{Track, Api, Position}

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
import org.scalajs.dom
import com.github.sjsf.leaflet.contextmenu.Implicits._

/**
 * Created by pappmar on 21/09/2015.
 */
class WebUI(store: Store) extends UI {
  override def show: Unit = {
    val body = dom.document.body
    Html
      .generate(
        Seq(
          Tab(
            id = "gpsTracks",
            icon = Seq(
              i(cls := "fa fa-location-arrow")
            ),
            panel = Seq(
              h1("GPS Tracks"),
              form(
                id := "gps-track-dropzone",
                cls := "dropzone",
                action := "/file-upload"
              )
            )
          ),
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
      .foreach( node => body.appendChild(node.render) )

    val map = LMap(
      "map",
      LMapOptions
      .contextmenu(true)
//        .contextmenuItems(
////          ItemOptions.text("Reload Tracks")
//        )
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

    async {
      val drawings : Seq[Seq[Position]] = await { store.loadDrawings }
      val drawLayer = LFeatureGroup(
        drawings.map { poly : Seq[Position] =>
          val p = LPolygon(
            poly.map { pos : Position => LLatLng(pos.lat, pos.lon) }.toJSArray
          )
          p.bindContextMenu(featureOptions(p))
          p.asInstanceOf[LILayer]
        }.toJSArray
      )

      drawLayer.addTo(map)
      layers.addOverlay(drawLayer, "Drawings")

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
