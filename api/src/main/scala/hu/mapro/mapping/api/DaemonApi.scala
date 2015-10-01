package hu.mapro.mapping.api

/**
 * Created by pappmar on 29/09/2015.
 */
object DaemonApi {

  sealed trait Message
  sealed trait DaemonToServerMessage extends Message
  sealed trait ServerToDaemonMessage extends Message
  case class OfferGpsTrackHash(hash: String) extends DaemonToServerMessage
  case class RequestGarminImg(hash: Option[String]) extends DaemonToServerMessage
  case class AcceptGpsTrackHash(hash: String) extends ServerToDaemonMessage
  case class ConfirmGpsTrackHash(hash: String) extends ServerToDaemonMessage
  case class UploadGpsTrack(data: Array[Byte])
  case class GarminImg(data: Array[Byte]) extends ServerToDaemonMessage





}
