package hu.mapro.mapping.actors

import akka.actor.{Actor, ActorLogging, ActorRef, Stash}

/**
 * Created by pappmar on 01/10/2015.
 */
class DaemonServerSocketActor(mainActor: ActorRef) extends Actor with Stash with ActorLogging {
  import DaemonServerSocketActor._

  override def receive: Receive = {
    case Daemon(daemon) =>
      unstashAll()
      log.debug("Daemon actor ready: {}", daemon)
      context.become(working(daemon))
    case _ =>
      stash()
  }

  def working(daemon: ActorRef) : Receive = {
    case msg => mainActor.tell(msg, daemon)
  }
}

object DaemonServerSocketActor {
  case class Daemon(actorRef: ActorRef)
}

