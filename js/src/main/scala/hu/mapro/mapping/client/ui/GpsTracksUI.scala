package hu.mapro.mapping.client.ui

import hu.mapro.mapping.Track
import monifu.concurrent.Implicits.globalScheduler
import com.github.sjsf.bootstrapnotify.notification
import com.github.sjsf.leaflet.{LPolyline, LILayer, LPolylineOptions, LMultiPolyline}
import com.github.sjsf.leaflet.sidebarv2.TabLike
import hu.mapro.mapping.Messaging.{GpsTracksRemoved, GpsTracksAdded, ServerToClientMessage}
import hu.mapro.mapping.client.Util
import monifu.reactive.Ack.Continue
import monifu.reactive.subjects.ReplaySubject
import org.scalajs.dom._
import org.scalajs.dom.raw.FormData
import org.scalajs.jquery._
import rx._

import scala.concurrent.Future
import scala.scalajs.js
import scalatags.JsDom.all._
import hu.mapro.mapping.client.Util._

/**
 * Created by pappmar on 28/09/2015.
 */
class GpsTracksUI(gpsTracksLayer: LMultiPolyline) extends TabLike {
  val id = "gpsTracks"
  val icon = Seq(
    i(cls := "fa fa-location-arrow")
  )
  val selectedFile = Var("")
  val serverToClient = ReplaySubject[ServerToClientMessage]()
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
        },
        ul(
        ).custom { ul =>

          var idMap = Map[Int, (Track, Node, LILayer)]()

          serverToClient.subscribe { msg =>
            msg match {
              case GpsTracksAdded(tracks) =>
                for (track <- tracks) {
                  val node = li(
                    track.positions.head.timestamp.toString,
                    span(
                      cls := "glyphicon glyphicon-eye-open"
                      ,
                      style := "cursor: pointer"
                    )
                    ,
                    span(
                      cls := "glyphicon glyphicon-trash"
                    )
                  ).render
                  ul.appendChild(
                    node
                  )
                  val layer = Util
                    .toPolyLine(track)
                    .setStyle(LPolylineOptions.color("red")._result)
                  gpsTracksLayer.addLayer(
                    layer
                  )

                  idMap += track.id -> (track, node, layer)
                }
              case GpsTracksRemoved(tracks) =>
                for (track <- tracks) {
                  idMap.get(track).foreach { case (_, node, layer) =>
                    ul.removeChild(node)
                    gpsTracksLayer.removeLayer(layer)
                    idMap -= track
                  }
                }

              case _ =>
            }
            Future(Continue)
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
