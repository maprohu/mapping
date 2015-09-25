package hu.mapro.mapping

import akka.actor._
import akka.stream.OverflowStrategy
import akka.stream.scaladsl._


case class OutgoingMessage(message: ServerToClientMessage)

trait MappingClients {
  def clientFlow(): Flow[ClientToServerMessage, OutgoingMessage, Unit]

  def injectMessage(message: OutgoingMessage): Unit
}

object MappingClients {
  def create(mainModule: MainServerModule): MappingClients = {
    // The implementation uses a single actor per chat to collect and distribute
    // chat messages. It would be nicer if this could be built by stream operations
    // directly.
    val mainActor =
      mainModule.actorSystem.actorOf(Props(classOf[DI], mainModule, Actors.Main), "mainActor")

    // Wraps the mainActor in a sink. When the stream to this sink will be completed
    // it sends the `ClientLeft` message to the mainActor.
    // FIXME: here some rate-limiting should be applied to prevent single users flooding the chat
    def clientInSink() = Sink.actorRef[ClientEvent](mainActor, ClientLeft())

    new MappingClients {
      def clientFlow(): Flow[ClientToServerMessage, OutgoingMessage, Unit] = {
        val in =
          Flow[ClientToServerMessage]
            .map(msg => ReceivedMessage(msg))
            .to(clientInSink())

        // The counter-part which is a source that will create a target ActorRef per
        // materialization where the mainActor will send its messages to.
        // This source will only buffer one element and will fail if the client doesn't read
        // messages fast enough.
        val out =
          Source.actorRef[OutgoingMessage](1, OverflowStrategy.fail)
            .mapMaterializedValue(mainActor ! NewClient(_))

        Flow.wrap(in, out)(Keep.none)
      }
      def injectMessage(message: OutgoingMessage): Unit = mainActor ! message // non-streams interface
    }
  }


}

class DIImplicit(mainModule: MainServerModule, actorName: Actors.Value) extends IndirectActorProducer {
  import mainModule._
  import Actors._
  override def produce(): Actor = actorName match {
    case Main => new MainActor
  }

  override def actorClass: Class[_ <: Actor] = classOf[MainActor]
}


private sealed trait ClientEvent
private case class NewClient(subscriber: ActorRef) extends ClientEvent
private case class ClientLeft() extends ClientEvent
private case class ReceivedMessage(message: ClientToServerMessage) extends ClientEvent


class MainActor(implicit db: DB) extends Actor {

  context.system.settings.config

  var clients = Set.empty[ActorRef]

  def receive: Receive = {
    case NewClient(subscriber) ⇒
      context.watch(subscriber)
      clients += subscriber
    case msg: ReceivedMessage    ⇒ //dispatch(msg.toChatMessage)
    case ClientLeft() ⇒ //sendAdminMessage(s"$person left!")
    case Terminated(sub)         ⇒ clients -= sub // clean up dead clients
  }

  def dispatch(msg: OutgoingMessage): Unit = clients.foreach(_ ! msg)
}


