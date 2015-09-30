package hu.mapro.mapping.daemon

import akka.actor.{ActorSystem, Props}

/**
 * Created by pappmar on 30/09/2015.
 */
class DaemonProcess(actorSystem: ActorSystem) {

  implicit val system = actorSystem


  def run = {
    actorSystem.actorOf(Props[DaemonActor])
  }



}
