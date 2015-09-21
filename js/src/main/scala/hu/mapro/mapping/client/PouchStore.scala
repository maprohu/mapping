package hu.mapro.mapping.client

import com.github.sjsf.leaflet.{LeafLeft, LGeoJson, LFeatureGroup}
import com.github.sjsf.pouchdb.PouchDB
import scala.scalajs.js.JSConverters._
import scala.scalajs.concurrent.JSExecutionContext.Implicits.queue

import scala.concurrent.Future

/**
 * Created by pappmar on 21/09/2015.
 */
class PouchStore extends Store {

  object Key extends Enumeration {
    val drawings = Value
  }
  val db = PouchDB.create("mapping")

  var drawingsRev : Option[String] = None

  override def loadDrawings: Future[LFeatureGroup] = {
    db.get(Key.drawings.toString)
      .map { json =>
        drawingsRev = Some(json._rev.toString)
        LGeoJson(json)
      }
      .recover { case t => LeafLeft.featureGroup()}
  }

  override def saveDrawings(featureGroup: LFeatureGroup): Future[Unit] =
    db.put(
      featureGroup.toGeoJSON(),
      Key.drawings.toString,
      drawingsRev.orUndefined
    ) map { res =>
      drawingsRev = Some(res.rev.toString)
      ()
    }
}
