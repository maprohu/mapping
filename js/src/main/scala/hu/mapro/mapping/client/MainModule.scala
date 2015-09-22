package hu.mapro.mapping.client

import com.github.sjsf.leaflet.LFeatureGroup
import com.softwaremill.macwire._
import hu.mapro.mapping.Position

import scala.concurrent.Future

/**
 * Created by pappmar on 21/09/2015.
 */
trait MainModule {
  lazy val ui = wire[WebUI]
  lazy val store = wire[PouchStore]

}

trait UI {
  def show
}

trait Store {
  type Polygons = Seq[Seq[Position]]

  def loadDrawings: Future[Polygons]
  def saveDrawings(featureGroup: Polygons): Future[Unit]
}