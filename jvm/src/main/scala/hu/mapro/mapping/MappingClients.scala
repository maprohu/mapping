package hu.mapro.mapping

import akka.actor._
import akka.stream.OverflowStrategy
import akka.stream.scaladsl._
import hu.mapro.mapping.DBActor.InitClient
import hu.mapro.mapping.MainActor.{ClientInitialized, ToClient}


trait MappingClients {
  def clientFlow(): Flow[ClientToServerMessage, ServerToClientMessage, Unit]

  def injectMessage(message: Any): Unit
}

object MappingClients {
  def create(actorSystem: ActorSystem): MappingClients = {
    import MainActor._
    // The implementation uses a single actor per chat to collect and distribute
    // chat messages. It would be nicer if this could be built by stream operations
    // directly.
    val mainActor =
      actorSystem.actorOf(Props[MainActor], "mainActor")

    // Wraps the mainActor in a sink. When the stream to this sink will be completed
    // it sends the `ClientLeft` message to the mainActor.
    // FIXME: here some rate-limiting should be applied to prevent single users flooding the chat
    def clientInSink() = Sink.actorRef[ClientEvent](mainActor, ClientLeft())

    new MappingClients {
      def clientFlow(): Flow[ClientToServerMessage, ServerToClientMessage, Unit] = {
        val in =
          Flow[ClientToServerMessage]
            .map(msg => ReceivedMessage(msg))
            .to(clientInSink())

        // The counter-part which is a source that will create a target ActorRef per
        // materialization where the mainActor will send its messages to.
        // This source will only buffer one element and will fail if the client doesn't read
        // messages fast enough.
        val out =
          Source.actorRef[ServerToClientMessage](1, OverflowStrategy.fail)
            .mapMaterializedValue(mainActor ! NewClient(_))

        Flow.wrap(in, out)(Keep.none)
      }
      def injectMessage(message: Any): Unit = mainActor ! message // non-streams interface
    }
  }


}



object MainActor {
  sealed trait ClientEvent
  case class NewClient(subscriber: ActorRef) extends ClientEvent
  case class ClientLeft() extends ClientEvent
  case class ReceivedMessage(message: ClientToServerMessage) extends ClientEvent
  case class ToClient(client: ActorRef, msg: ServerToClientMessage)
  case class ToAllClients(msg: ServerToClientMessage)
  case class ClientInitialized(client: ActorRef)

}

class MainActor extends Actor {
  import MainActor._

  val db = context.actorOf(Props[DBActor], "db")

  var clients = Set.empty[ActorRef]

  def receive: Receive = {
    case ToClient(client, msg) => client ! msg
    case NewClient(client) =>
      db ! InitClient(client)
    case ClientInitialized(client) =>
      context.watch(client)
      clients += client

    case msg: ReceivedMessage    ⇒ //dispatch(msg.toChatMessage)
    case ClientLeft() ⇒ //sendAdminMessage(s"$person left!")
    case Terminated(sub)         ⇒ clients -= sub // clean up dead clients
  }

  def dispatch(msg: ServerToClientMessage): Unit = clients.foreach(_ ! msg)
}

object DBActor {
  case class InitClient(client: ActorRef)

}

class DBActor extends Actor {

  val db = new DBPostgres

  var allGpsTracks = db.allGpsTracks

  import DBActor._
  import MainActor._

  def receive = {
    case InitClient(client) =>
      allGpsTracks.onSuccess { case tracks =>
        for (track <- tracks) context.parent ! ToClient(client, GpsTrackAdded(track))
        context.parent ! ClientInitialized(client)
      }
  }
}



