package hu.mapro.mapping.fit

import java.net.URL

import com.garmin.fit.{RecordMesgListener, MesgBroadcaster, Decode, RecordMesg}

import scala.collection.mutable
import scala.collection.mutable.ListBuffer
import scala.collection.immutable
import resource._

/**
 * Created by pappmar on 14/09/2015.
 */
object Fit {

  def readRecords(url: URL) : immutable.Seq[RecordMesg] = {
    val decode = new Decode
    val mesgBroadcaster = new MesgBroadcaster()
    val result = new ListBuffer[RecordMesg]
    mesgBroadcaster.addListener(new RecordMesgListener {
      override def onMesg(mesg: RecordMesg): Unit =
        result += mesg
    })
    for (input <- managed(url.openStream())) {
      decode.read(input, mesgBroadcaster)
    }
    result.toList
  }

}
