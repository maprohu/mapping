package hu.mapro.mapping.client

import com.github.sjsf.leaflet.LFeatureGroup
import com.softwaremill.macwire._
import hu.mapro.mapping.Messaging.Polygons
import hu.mapro.mapping.{Coordinates, Position}

import scala.concurrent.Future

/**
 * Created by pappmar on 21/09/2015.
 */
trait MainClientModule {
  lazy val ui = wire[WebUI]
  lazy val store = wire[PouchStore]

}

trait UI {
  def show
}

trait Store {
  def loadDrawings: Future[Polygons]
  def saveDrawings(featureGroup: Polygons): Future[Unit]
}