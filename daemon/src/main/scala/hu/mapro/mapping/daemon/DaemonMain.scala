package hu.mapro.mapping.daemon

import com.github.kxbmap.configs._
import com.typesafe.config.ConfigFactory

import scala.concurrent.duration.{Duration, FiniteDuration}
import scala.io.StdIn

/**
 * Created by pappmar on 29/09/2015.
 */
object DaemonMain extends App with DaemonModule{

  val config = ConfigFactory.load()
  val waitBeforeInput = config.get[Duration]("waitBeforeInput").asInstanceOf[FiniteDuration]

  daemonProcess.run

  println("Daemon running. Press ENTER to quit.")
  Thread.sleep(waitBeforeInput.toMillis)
  StdIn.readLine()

}
