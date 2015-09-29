package hu.mapro.mapping.api

/**
 * Created by pappmar on 29/09/2015.
 */
object DaemonApi {

  sealed trait Message
  case class OfferGpsTrackHash(hash: String) extends Message
  case class AcceptGpsTrackHash(hash: String) extends Message




}
