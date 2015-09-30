package hu.mapro.mapping.daemon

import akka.actor.ActorSystem
import com.softwaremill.macwire._

/**
 * Created by pappmar on 30/09/2015.
 */
trait DaemonModule {
  lazy val actorSystem = ActorSystem()
  lazy val daemonProcess = wire[DaemonProcess]




}
