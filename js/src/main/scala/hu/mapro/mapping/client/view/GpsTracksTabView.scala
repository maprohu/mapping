package hu.mapro.mapping.client.view

import com.github.sjsf.bootstrapnotify.notification
import com.github.sjsf.leaflet.sidebarv2.TabLike
import com.github.sjsf.leaflet.{LILayer, LMultiPolyline, LPolylineOptions}
import hu.mapro.mapping.Messaging.{GpsTracksAdded, GpsTracksRemoved, ServerToClientMessage}
import hu.mapro.mapping.Track
import hu.mapro.mapping.client.Util
import hu.mapro.mapping.client.Util._
import monifu.concurrent.Implicits.globalScheduler
import monifu.reactive.Ack.Continue
import monifu.reactive.{Ack, Observable}
import monifu.reactive.channels.ReplayChannel
import monifu.reactive.subjects.ReplaySubject
import org.scalajs.dom._
import org.scalajs.dom.raw.FormData
import org.scalajs.jquery._
import rx._

import scala.concurrent.Future
import scala.scalajs.js
import scalatags.JsDom.all._

/**
 * Created by pappmar on 28/09/2015.
 */
class GpsTracksTabView extends TabLike {

  val uiSelectFile = ReplaySubject[FileList]()
  val uiDeleteTrack = ReplaySubject[Int]()
  val uiHideTrack = ReplaySubject[Int]()
  val uiShowTrack = ReplaySubject[Int]()

  val ctrlAddTrack = ReplaySubject[Track]()
  val ctrlDeleteTrack = ReplaySubject[Int]()


  val id = "gpsTracks"
  val icon = Seq(
    i(cls := "fa fa-location-arrow")
  )
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
            uiSelectFile.onNext(input.files)
          }
        },
        ul(
        ).custom { ul =>

          var nodeMap = Map[Int, Node]()

          ctrlAddTrack.subscribe(track => Future {
            val trackVisible = Var(true)
            val node = li(
              track.positions.head.timestamp.toString,
              span(
                cls := "glyphicon glyphicon-eye-open",
                style := "cursor: pointer"
              ).visibleWhen(trackVisible)
              ,
              span(
                cls := "glyphicon glyphicon-eye-close",
                style := "cursor: pointer"
              ).hiddenWhen(trackVisible)
              ,
              span(
                cls := "glyphicon glyphicon-trash"
              )
            ).render
            ul.appendChild(
              node
            )

            nodeMap += track.id -> node

            Ack.Continue
          })

        }
      )
    )


  )
}
