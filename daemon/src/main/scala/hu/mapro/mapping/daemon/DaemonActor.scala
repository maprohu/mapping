package hu.mapro.mapping.daemon

import java.io.File
import java.nio.file.{Files, Paths}

import akka.actor.{Actor, ActorLogging, Props}
import com.github.kxbmap.configs._
import hu.mapro.mapping.api.DaemonApi.{AcceptGpsTrackHash, GarminImg, OfferGpsTrackHash, UploadGpsTrack}
import hu.mapro.mapping.api.Util

import scala.concurrent.duration._
import scala.util.Try

/**
 * Created by pappmar on 30/09/2015.
 */

class DaemonActor extends Actor with ActorLogging {

  import DaemonActor._
  import context.dispatcher

  val socket = context.actorOf(Props[DaemonSocketClientActor])

  val config = context.system.settings.config

  val fitPath = config.get[List[String]]("fitPath")
  val imgFile = config.get[String]("imgFile")
  val checkInterval = config.get[Duration]("checkInterval").asInstanceOf[FiniteDuration]

  context.system.scheduler.schedule(
    0 millis,
    checkInterval,
    self,
    Check
  )

  override def receive: Receive = checking

  val checking : Receive = {
    case Check =>
      log.info("Check triggered...")
      val files = for {
        dirName <- fitPath
        dirFile = new File(dirName)
        if dirFile.exists() && dirFile.isDirectory
        fitFile <- dirFile.listFiles()
        hash = Util.hash(fitFile)
      } yield {
          log.info("Offering file {} with hash: {}", fitFile, hash)
          socket ! OfferGpsTrackHash(hash)
          hash -> fitFile
      }

      context.become(hashesSent(files.toMap) orElse checking)
    case GarminImg(data) =>
      Try(Files.write(Paths.get(imgFile), data))

  }

  def hashesSent(fileMap: Map[String, File]) : Receive = {
    case AcceptGpsTrackHash(hash) =>
      fileMap.get(hash).foreach { f =>
        socket ! UploadGpsTrack(Files.readAllBytes(f.toPath))
      }


  }
}

object DaemonActor {
  object Check
}
