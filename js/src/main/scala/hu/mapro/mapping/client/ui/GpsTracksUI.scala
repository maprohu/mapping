package hu.mapro.mapping.client.ui

import hu.mapro.mapping.Track
import hu.mapro.mapping.client.view.GpsTracksTabView
import monifu.concurrent.Implicits.globalScheduler
import com.github.sjsf.bootstrapnotify.notification
import com.github.sjsf.leaflet.{LPolyline, LILayer, LPolylineOptions, LMultiPolyline}
import com.github.sjsf.leaflet.sidebarv2.TabLike
import hu.mapro.mapping.Messaging._
import hu.mapro.mapping.client.Util
import monifu.reactive.Ack.Continue
import monifu.reactive.Observer
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

class GpsTracksUI(clientToServer: Observer[ClientToServerMessage]) {

  val gpsTracksLayer = LMultiPolyline(
    js.Array(),
    LPolylineOptions.color("pink")._result
  )
  val tab = new GpsTracksTabView

  val serverToClient = ReplaySubject[ServerToClientMessage]()

  var layerMap = Map[Int, LILayer]()
  
  serverToClient.subscribe { msg =>
    msg match {
      case GpsTracksAdded(tracks) =>
        for (track <- tracks) {
          tab.ctrlAddTrack.onNext(track)

          val layer = Util
            .toPolyLine(track)
            .setStyle(LPolylineOptions.color("red")._result)

          layerMap += track.id -> layer
        }
      case GpsTracksRemoved(tracks) =>
        for (track <- tracks) {
          tab.ctrlDeleteTrack.onNext(track)
          layerMap.get(track).foreach { layer =>
            gpsTracksLayer.removeLayer(layer)
            layerMap -= track
          }
        }
    }
    Future(Continue)
  }

  tab.uiSelectFile.subscribe { files =>
      println("select")
      val formData = new FormData()
      jQuery.each(files, { (key: js.Any, value: js.Any) =>
        println("each")
        formData.append(key, value)
        ().asInstanceOf[js.Any]
      })
      val s = (new js.Object).asInstanceOf[JQueryAjaxSettings]
      s.url = "upload/gps-data"
      s.`type` = "POST"
      s.data = formData
      s.cache = false
      s.dataType = "json"
      s.processData = false
      s.contentType = false
      jQuery.ajax(s)

      Future(Continue)
  }



  tab.uiShowTrack.subscribe { trackId =>
    gpsTracksLayer.addLayer(layerMap(trackId))

    Future(Continue)
  }

  tab.uiHideTrack.subscribe { trackId =>
    gpsTracksLayer.removeLayer(layerMap(trackId))

    Future(Continue)
  }

  tab.uiDeleteTrack.subscribe { trackId =>
    clientToServer.onNext(DeleteTrack(trackId))

    Future(Continue)
  }
}
