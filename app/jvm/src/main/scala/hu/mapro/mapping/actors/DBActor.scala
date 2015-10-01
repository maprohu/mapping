package hu.mapro.mapping.actors

import akka.actor._
import akka.pattern.pipe
import com.google.common.io.ByteSource
import hu.mapro.mapping.Messaging._
import hu.mapro.mapping._
import hu.mapro.mapping.api.DaemonApi.{AcceptGpsTrackHash, ConfirmGpsTrackHash}


object DBActor {
  case class Deps(db: DB, gspTracks: Seq[(Track, String)])
  case class InitClient(client: ActorRef)
  case class GpsTrackSaved(track: Track, hash: String)
  case class GpsTrackOffered(hash: String, from: ActorRef)

  object GetAllTracks

}

class DBActor extends Actor with Stash with ActorLogging {

  import DBActor._
  import MainActor._
  import context.dispatcher

  for {
    db <- DBPostgres()
    tracks <- db.allGpsTracks
  } self ! Deps(db, tracks)

  def receive = {
    case Deps(db, tracks) =>
      unstashAll()
      context.become(working(db, tracks.map(t => t._1.id -> t).toMap))
    case _ => stash()
  }

  def working(db: DB, gpsTracks: Map[Int, (Track, String)]) : Receive = {
    val tracksSeq: Seq[Track] = gpsTracks.values.map(_._1).toSeq
    val hashSet = gpsTracks.values.map(_._2).toSet

    {
      case InitClient(client) =>
        context.parent ! ToClient(client, GpsTracksAdded(tracksSeq))
        context.parent ! ClientInitialized(client)
      case GpsTrackUploaded(msg) =>
        db
          .saveGpsTrack(msg)
          .map{case (id, hash) =>
          GpsTrackSaved(Track(Fit.parseGpsPositions(ByteSource.wrap(msg)), id), hash)
        }
          .pipeTo(self)
      case GpsTrackSaved(track, hash) =>
        context.parent ! GpsTracksChanged
        context.parent !
          ToAllClients(
            GpsTracksAdded(Seq(track))
          )
        context.become(working(db, gpsTracks + (track.id -> (track, hash))))
      case DeleteTrack(trackId) =>
        db
          .deleteGpsTrack(trackId)
          .map { _ => GpsTracksRemoved(Seq(trackId)) }
          .pipeTo(self)
      case msg @ GpsTracksRemoved(trackIds) =>
        context.parent ! GpsTracksChanged
        context.parent ! ToAllClients(msg)
        context.become(working(db, gpsTracks -- trackIds))
      case GpsTrackOffered(hash, from) =>
        log.debug("Gps track offered: {}", hash)
        if (!hashSet.contains(hash)) from ! AcceptGpsTrackHash(hash)
        else from ! ConfirmGpsTrackHash(hash)


      case GetAllTracks =>
        log.debug("All tracks requested.")
        sender ! tracksSeq

    }
  }

}



