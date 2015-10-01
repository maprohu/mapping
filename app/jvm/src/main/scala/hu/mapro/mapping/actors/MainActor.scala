package hu.mapro.mapping.actors

import akka.actor._
import hu.mapro.mapping.Messaging._
import hu.mapro.mapping.actors.DBActor._
import hu.mapro.mapping.api.DaemonApi.{OfferGpsTrackHash, RequestGarminImg}

object MainActor {
  case class NewClient(subscriber: ActorRef)
  case class ClientLeft()

  case class ToClient(client: ActorRef, msg: ServerToClientMessage)
  case class ToAllClients(msg: ServerToClientMessage)
  case class GpsTrackUploaded(data: Array[Byte])
  object UploadComplete
  object GpsTracksChanged


  case class ClientInitialized(client: ActorRef)

}

class MainActor extends Actor with ActorLogging {

  import MainActor._

  val db = context.actorOf(Props[DBActor], "db")

  val osm = context.actorOf(Props(classOf[OSMActor], db), "osm")

  var clients = Set.empty[ActorRef]

  def receive: Receive = {
    case msg @ NewClient(client) =>
      db ! InitClient(client)
      osm ! msg

    case ClientInitialized(client) =>
      context.watch(client)
      clients += client
    case ToClient(client, msg) => client ! msg
    case ToAllClients(msg) => clients.foreach(_ ! msg)

    case msg:GpsTrackUploaded =>
      db.tell(msg, sender())

    case msg:RequestGarminImg =>
      osm.tell(msg, sender())
    case msg:FetchCycleways =>
      osm ! msg

    case msg:DeleteTrack =>
      db ! msg

    case OfferGpsTrackHash(hash) =>
      db ! GpsTrackOffered(hash, sender)

    case msg@GpsTracksChanged =>
      osm ! msg


    case ClientLeft() ⇒ //sendAdminMessage(s"$person left!")
    case Terminated(sub)         ⇒ clients -= sub // clean up dead clients
  }

  def dispatch(msg: ServerToClientMessage): Unit = clients.foreach(_ ! msg)
}



