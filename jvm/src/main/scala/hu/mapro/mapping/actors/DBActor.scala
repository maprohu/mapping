package hu.mapro.mapping.actors

import akka.actor._
import com.google.common.io.ByteSource
import akka.pattern.pipe
import hu.mapro.mapping._
import hu.mapro.mapping.Messaging._





object DBActor {
  case class Deps(db: DB, gspTracks: Seq[Track])
  case class InitClient(client: ActorRef)
  case class GpsTrackSaved(track: Track)

}

class DBActor extends Actor with Stash {

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
      context.become(working(db, tracks))
    case _ => stash()
  }

  def working(db: DB, gpsTracks: Seq[Track]) : Receive = {
    case InitClient(client) =>
      context.parent ! ToClient(client, GpsTracksAdded(gpsTracks))
      context.parent ! ClientInitialized(client)
    case GpsTrackUploaded(msg) =>
      db
        .saveGpsTrack(msg)
        .map{id =>
          GpsTrackSaved(Track(Fit.parseGpsPositions(ByteSource.wrap(msg)), id))
        }
        .pipeTo(self)
    case GpsTrackSaved(track) =>
      context.parent !
        ToAllClients(
          GpsTracksAdded(Seq(track))
        )
      context.become(working(db, track +: gpsTracks))

  }

}



