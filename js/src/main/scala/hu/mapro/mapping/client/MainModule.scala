package hu.mapro.mapping.client

import com.github.sjsf.leaflet.LFeatureGroup
import com.softwaremill.macwire._

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
  def loadDrawings: Future[LFeatureGroup]
  def saveDrawings(featureGroup: LFeatureGroup): Future[Unit]
}