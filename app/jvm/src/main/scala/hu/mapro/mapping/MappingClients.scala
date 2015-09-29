package hu.mapro.mapping

import akka.actor._
import akka.stream.OverflowStrategy
import akka.stream.scaladsl._
import com.google.common.io.ByteSource
import com.google.common.primitives.Bytes
import akka.pattern.pipe
import hu.mapro.mapping.Messaging._
import hu.mapro.mapping.actors.{MainActor, OSMActor}


trait MappingClients {
  def clientFlow(): Flow[ClientToServerMessage, ServerToClientMessage, Unit]

  def injectMessage(message: Any): Unit

  def mainActor : ActorRef
}

object MappingClients {
  def create(actorSystem: ActorSystem): MappingClients = {
    import MainActor._

    new MappingClients {
      // The implementation uses a single actor per chat to collect and distribute
      // chat messages. It would be nicer if this could be built by stream operations
      // directly.
      val mainActor =
        actorSystem.actorOf(Props[MainActor], "mainActor")

      // Wraps the mainActor in a sink. When the stream to this sink will be completed
      // it sends the `ClientLeft` message to the mainActor.
      // FIXME: here some rate-limiting should be applied to prevent single users flooding the chat
      def clientInSink() = Sink.actorRef[Any](mainActor, ClientLeft())

      def clientFlow(): Flow[ClientToServerMessage, ServerToClientMessage, Unit] = {
        val in =
          Flow[ClientToServerMessage]
            .to(clientInSink())

        // The counter-part which is a source that will create a target ActorRef per
        // materialization where the mainActor will send its messages to.
        // This source will only buffer one element and will fail if the client doesn't read
        // messages fast enough.
        val out =
          Source.actorRef[ServerToClientMessage](100, OverflowStrategy.fail)
            .mapMaterializedValue(mainActor ! NewClient(_))

        Flow.wrap(in, out)(Keep.none)
      }
      def injectMessage(message: Any): Unit = mainActor ! message // non-streams interface
    }
  }


}




