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


  override def receive: Receive = waitingForConnection(Set(), None)

  def waitingForConnection(done: Set[String], checkTask: Option[Cancellable]) : Receive =  {
    case Connected =>
      checkTask.foreach(_.cancel())
      val newCheckTask = context.system.scheduler.schedule(
        0 millis,
        checkInterval,
        self,
        Check
      )
      context.become(checking(newCheckTask, done))
  }

  def checking(checkTask: Cancellable, done: Set[String]) : Receive = {
    ({
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

        processFiles(checkTask, files.toMap, done)
      case GarminImg(data) =>
        log.info("Receveived Garmin IMG file.")
        for (
          imgFile <- imgFiles
          if new File(imgFile).getParentFile.exists()
        ) {
          val result = Try(Files.write(Paths.get(imgFile), data))
          log.info("Result of saving Garming IMG file: {}", result)
        }
      case GarminImgUpToDate =>
        log.info("Garmin IMG is up to date.")
      case Disconnected =>
        checkTask.cancel()
        context.become(waitingForConnection(done, None))
    }:Receive) orElse waitingForConnection(done, Some(checkTask))
  }

  def hashesSent(
    checkTask: Cancellable,
    fileMap: Map[String, File],
    done: Set[String]
  ) : Receive = {
    ({
      case AcceptGpsTrackHash(hash) =>
        fileMap.get(hash).foreach { f =>
          log.info("Uploading file {} with hash {}", f, hash)
          socket ! UploadGpsTrack(Files.readAllBytes(f.toPath))
        }
      case ConfirmGpsTrackHash(hash) =>
        log.debug("Gps Track confirmed: {}", hash)
        val newFileMap = fileMap - hash
        val newDone = done + hash
        processFiles(checkTask, newFileMap, newDone)
    }:Receive) orElse checking(checkTask, done)
  }

  def processFiles(checkTask: Cancellable, fileMap: Map[String, File], done: Set[String]): Unit = {
    if (fileMap.isEmpty) {
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
      context.become(checking(checkTask, done))
    } else {
      context.become(hashesSent(checkTask, fileMap, done))
    }
  }
}

object DaemonActor {
  object Check
  object Connected
  object Disconnected
}
