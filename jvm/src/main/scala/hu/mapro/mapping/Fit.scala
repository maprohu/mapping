package hu.mapro.mapping

import com.garmin.fit.{Decode, MesgBroadcaster, RecordMesg, RecordMesgListener}
import com.google.common.io.ByteSource
import resource._

import scala.collection.immutable
import scala.collection.mutable.ListBuffer

/**
 * Created by pappmar on 14/09/2015.
 */
object Fit {

  def readRecords(source: ByteSource) : immutable.Seq[RecordMesg] = {
    val decode = new Decode
    val mesgBroadcaster = new MesgBroadcaster()
    val result = new ListBuffer[RecordMesg]
    mesgBroadcaster.addListener(new RecordMesgListener {
      override def onMesg(mesg: RecordMesg): Unit =
        result += mesg
    })
    for (input <- managed(source.openStream())) {
      decode.read(input, mesgBroadcaster)
    }
    result.toList
  }

  final def semiToDeg(semi : Int) : Double =
    semi * (180.0 / math.pow(2, 31) )

  def parseGpsTrack(resource: ByteSource): Track = {
    Track(
      readRecords(resource)
        .filter( r => r.getPositionLat !=null & r.getPositionLong != null)
        .map { r =>
        Position(
          semiToDeg(r.getPositionLat),
          semiToDeg(r.getPositionLong),
          r.getTimestamp.getTimestamp
        )
      }
    )
  }
}
