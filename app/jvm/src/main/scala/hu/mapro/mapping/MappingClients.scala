package hu.mapro.mapping

import akka.actor._
import akka.stream.OverflowStrategy
import akka.stream.scaladsl._
import com.google.common.io.ByteSource
import com.google.common.primitives.Bytes
import akka.pattern.pipe
import hu.mapro.mapping.Messaging._
import hu.mapro.mapping.actors.{MainActor, OSMActor}
import hu.mapro.mapping.api.DaemonApi._



class MappingClients(implicit actorSystem: ActorSystem) {
  import MainActor._

  // The implementation uses a single actor per chat to collect and distribute
  // chat messages. It would be nicer if this could be built by stream operations
  // directly.
  val mainActor =
    actorSystem.actorOf(Props[MainActor], "mainActor")


  def clientFlow(): Flow[ClientToServerMessage, ServerToClientMessage, Unit] = {
    val in =
      Flow[ClientToServerMessage]
        .to(Sink.actorRef[Any](mainActor, ClientLeft()))

    // The counter-part which is a source that will create a target ActorRef per
    // materialization where the mainActor will send its messages to.
    // This source will only buffer one element and will fail if the client doesn't read
    // messages fast enough.
    val out =
      Source.actorRef[ServerToClientMessage](100, OverflowStrategy.fail)
        .mapMaterializedValue(mainActor ! NewClient(_))

    Flow.wrap(in, out)(Keep.none)
  }

  def daemonFlow(): Flow[Any, ServerToDaemonMessage, Unit] = {
    val in =
      Flow[Any]
        .to(Sink.actorRef[Any](mainActor, ClientLeft()))

    val out =
      Source.actorRef[ServerToDaemonMessage](100, OverflowStrategy.fail)

    Flow.wrap(in, out)(Keep.none)
  }

  def injectMessage(message: Any): Unit = mainActor ! message // non-streams interface

}




