package hu.mapro.mapping.daemon

import java.io.File
import java.nio.file.{Files, Paths}

import akka.actor.{Actor, ActorLogging, Cancellable, Props}
import com.github.kxbmap.configs._
import hu.mapro.mapping.api.DaemonApi._
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
  val imgFiles = config.get[List[String]]("imgFiles")
  val checkInterval = config.get[Duration]("checkInterval").asInstanceOf[FiniteDuration]


  override def receive: Receive = waitingForConnection(Set())

  def waitingForConnection(done: Set[String]) : Receive =  {
    case Connected =>
      val checkTask = context.system.scheduler.schedule(
        0 millis,
        checkInterval,
        self,
        Check
      )
      context.become(checking(checkTask, done))
  }

  def checking(checkTask: Cancellable, done: Set[String]) : Receive = {
    case Check =>
      log.info("Check triggered...")
      val files = for {
        dirName <- fitPath
        dirFile = new File(dirName)
        if dirFile.exists() && dirFile.isDirectory
        fitFile <- dirFile.listFiles()
        hash = Util.hash(fitFile)
        if !done.contains(hash)
      } yield {
          log.info("Offering file {} with hash: {}", fitFile, hash)
          socket ! OfferGpsTrackHash(hash)
          hash -> fitFile
      }

      context.become(hashesSent(checkTask, files.toMap, done) orElse checking(checkTask, done))
    case GarminImg(data) =>
      log.info("Receveived Garmin IMG file.")
      for (imgFile <- imgFiles) {
        Try(Files.write(Paths.get(imgFile), data))
      }
    case Disconnected =>
      context.become(waitingForConnection(done))
  }

  def hashesSent(
    checkTask: Cancellable,
    fileMap: Map[String, File],
    done: Set[String]
  ) : Receive = {
    case AcceptGpsTrackHash(hash) =>
      fileMap.get(hash).foreach { f =>
        log.info("Uploading file {} with hash {}", f, hash)
        socket ! UploadGpsTrack(Files.readAllBytes(f.toPath))
      }
    case ConfirmGpsTrackHash(hash) =>
      log.debug("Gps Track confirmed: {}", hash)
      val newFileMap = fileMap - hash
      val newDone = done + hash
      if (newFileMap.isEmpty) {
        for {
          imgFile <- imgFiles
          file = new File(imgFile)
          parentFile = file.getParentFile
          if parentFile.exists() && parentFile.isDirectory
        } {
          log.info("Requesting Garming IMG")
          if (file.exists()) {
            socket ! RequestGarminImg(Some(Util.hash(file)))
          } else {
            socket ! RequestGarminImg(None)
          }
        }
        context.become(checking(checkTask, newDone))
      } else {
        context.become(hashesSent(checkTask, newFileMap, newDone))
      }
  }
}

object DaemonActor {
  object Check
  object Connected
  object Disconnected
}
