package hu.mapro.mapping.daemon

import java.io.File
import java.net.URI
import javax.websocket.{ClientEndpointConfig, Endpoint, EndpointConfig, Session}

import com.github.kxbmap.configs._
import com.typesafe.config.ConfigFactory
import hu.mapro.mapping.api.Util
import monifu.concurrent.Implicits.globalScheduler
import monifu.reactive.Observable
import org.glassfish.tyrus.client.ClientManager
import org.glassfish.tyrus.client.ClientManager.ReconnectHandler

import scala.concurrent.Future
import scala.concurrent.duration.{Duration, FiniteDuration}
import scala.io.StdIn

/**
 * Created by pappmar on 29/09/2015.
 */
object DaemonMain extends App {

  val config = ConfigFactory.load()

  val fitPath = config.get[List[String]]("fitPath")
  val checkInterval = config.get[Duration]("checkInterval").asInstanceOf[FiniteDuration]
  val waitBeforeInput = config.get[Duration]("waitBeforeInput").asInstanceOf[FiniteDuration]
  val serverUrl = config.get[String]("serverUrl")

  println(fitPath)
  println(checkInterval)


  Observable
    .interval(checkInterval)
    .flatMap(_ => Observable.fromIterable(fitPath))
    .map(new File(_))
    .filter(f => f.exists() && f.isDirectory)
    .flatMap(f => Observable.fromIterable(f.listFiles()))
    .map(f => (f, Util.hash(f)))

    .foreach(println(_))




  println("Daemon running. Press ENTER to quit.")
  Thread.sleep(waitBeforeInput.toMillis)
  StdIn.readLine()

  def connect : Future[Session] = Future {
    val wscfg = ClientEndpointConfig.Builder.create().build()
    val client = ClientManager.createClient()
    val rh = new ReconnectHandler()
    client.connectToServer(
      new Endpoint() {
        override def onOpen(session: Session, config: EndpointConfig): Unit = {
        }
      },
      wscfg,
      new URI(serverUrl)
    )
  }
}
