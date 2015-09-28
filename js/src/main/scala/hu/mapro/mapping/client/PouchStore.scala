package hu.mapro.mapping.client

import com.github.sjsf.leaflet.{LLatLng, LeafLeft, LGeoJson, LFeatureGroup}
import com.github.sjsf.pouchdb.PouchDB
import hu.mapro.mapping.Messaging.Polygons
import hu.mapro.mapping.{Coordinates, Position}
import scala.scalajs.js
import scala.scalajs.js.JSConverters._
import scala.scalajs.concurrent.JSExecutionContext.Implicits.queue

import scala.concurrent.Future
import scala.scalajs.js._
import scala.scalajs.js.annotation.{ScalaJSDefined, JSExport}

/**
 * Created by pappmar on 21/09/2015.
 */
class PouchStore extends Store {

  object Key extends Enumeration {
    val drawings = Value
  }
  val db = PouchDB.create("mapping")

  var drawingsRev : Option[String] = None

  override def loadDrawings: Future[Polygons] = {
    db.get(Key.drawings.toString)
      .map { json =>
        val doc = json.asInstanceOf[Drawings with PouchDoc]
        drawingsRev = Some(doc._rev)
        doc.polygons.toSeq.map(_.toSeq.map(ll => Coordinates(ll.lat, ll.lng)))
      }
      .recover { case t => Seq() }
  }

  override def saveDrawings(featureGroup: Polygons): Future[Unit] =
    db.put(
      new Drawings {
        val polygons = featureGroup.toJSArray.map(_.toJSArray.map(p => LLatLng(p.lat, p.lon)))
      },
      Key.drawings.toString,
      drawingsRev.orUndefined
    ) map { res =>
      drawingsRev = Some(res.asInstanceOf[PouchAck].rev)
      ()
    }
}

@ScalaJSDefined
trait PouchDoc extends js.Object {

  val _id: String

  val _rev: String

}

@ScalaJSDefined
trait PouchAck extends js.Object {

  val id: String

  val rev: String

}

@ScalaJSDefined
trait Drawings extends js.Object {
  val polygons: js.Array[js.Array[LLatLng]]
}


