package hu.mapro.mapping.daemon

import java.net.URI
import java.nio.ByteBuffer
import javax.websocket.MessageHandler.Whole
import javax.websocket._

import akka.actor.{Actor, ActorLogging}
import com.github.kxbmap.configs._
import hu.mapro.mapping.api.DaemonApi.{DaemonToServerMessage, GarminImg, ServerToDaemonMessage, UploadGpsTrack}
import org.glassfish.tyrus.client.ClientManager.ReconnectHandler
import org.glassfish.tyrus.client.{ClientManager, ClientProperties}

/**
 * Created by pappmar on 30/09/2015.
 */
class DaemonSocketClientActor extends Actor with ActorLogging {
  import DaemonSocketClientActor._

  val serverUrl = context.system.settings.config.get[String]("serverUrl")

  val wscfg = ClientEndpointConfig.Builder.create().build()
  val client = ClientManager.createClient()
  client.getProperties.put(ClientProperties.RECONNECT_HANDLER, new ReconnectHandler() {
    override def onConnectFailure(exception: Exception): Boolean = {
      log.warning("Connection failure: {}", exception.getMessage)
      self ! Disconnect
      true
    }
    override def onDisconnect(closeReason: CloseReason): Boolean = {
      log.warning("Disconnected: {}", closeReason.getReasonPhrase)
      self ! Disconnect
      true
    }
  })
  log.info("Connecting to {} ...", serverUrl)
  client.asyncConnectToServer(
    new Endpoint() {
      override def onOpen(session: Session, config: EndpointConfig): Unit = {
        log.info("Connected to {}", serverUrl)
        session.addMessageHandler(new Whole[String] {
          override def onMessage(message: String): Unit = {
            context.parent ! upickle.default.read[ServerToDaemonMessage](message)
          }
        })
        session.addMessageHandler(new Whole[ByteBuffer] {
          override def onMessage(message: ByteBuffer): Unit = {
            val b = new Array[Byte](message.remaining())
            message.get(b)
            context.parent ! GarminImg(b)
          }
        })
        self ! Connect(session)
      }

      override def onError(session: Session, thr: Throwable): Unit = {
        log.error(thr, thr.getMessage)
      }
    },
    wscfg,
    new URI(serverUrl)
  )

  val disconnected : Receive = {
    case Connect(session) =>
      context.parent ! DaemonActor.Connected
      context.become(connected(session))

  }


  def connected(session: Session) : Receive = {
    case msg:DaemonToServerMessage =>
      session.getBasicRemote.sendText(upickle.default.write(msg))
    case UploadGpsTrack(data) =>
      session.getBasicRemote.sendBinary(ByteBuffer.wrap(data))

    case Disconnect =>
      context.parent ! DaemonActor.Disconnected
      context.become(disconnected)

  }

  override def receive: Receive = disconnected
}

object DaemonSocketClientActor {
  case class Connect(session: Session)
  object Disconnect
}
