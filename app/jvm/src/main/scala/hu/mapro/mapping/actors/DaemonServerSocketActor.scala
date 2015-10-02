package hu.mapro.mapping.actors

import akka.actor.{Actor, ActorLogging, ActorRef, Stash}
import hu.mapro.mapping.api.DaemonApi.Tick

import scala.concurrent.duration._

/**
 * Created by pappmar on 01/10/2015.
 */
class DaemonServerSocketActor(mainActor: ActorRef) extends Actor with Stash with ActorLogging {
  import DaemonServerSocketActor._
  import context.dispatcher
  def tick = context.system.scheduler.scheduleOnce(15 seconds, self, Tick)

  override def receive: Receive = {
    case Daemon(daemon) =>
      unstashAll()
      log.debug("Daemon actor ready: {}", daemon)
      tick
      context.become(working(daemon))
    case _ =>
      stash()
  }

  def working(daemon: ActorRef) : Receive = {
    case Tick =>
      daemon ! Tick
      tick
    case msg => mainActor.tell(msg, daemon)
  }
}

object DaemonServerSocketActor {
  case class Daemon(actorRef: ActorRef)
}

