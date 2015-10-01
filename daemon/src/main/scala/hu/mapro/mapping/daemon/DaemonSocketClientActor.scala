package hu.mapro.mapping.daemon

import java.net.URI
import java.nio.ByteBuffer
import javax.websocket.MessageHandler.Whole
import javax.websocket._

import akka.actor.{Actor, ActorLogging, Stash}
import akka.pattern._
import com.github.kxbmap.configs._
import hu.mapro.mapping.api.DaemonApi.{DaemonToServerMessage, GarminImg, ServerToDaemonMessage, UploadGpsTrack}
import org.glassfish.tyrus.client.ClientManager.ReconnectHandler
import org.glassfish.tyrus.client.{ClientManager, ClientProperties}

import scala.concurrent.{Future, Promise}

/**
 * Created by pappmar on 30/09/2015.
 */
class DaemonSocketClientActor extends Actor with Stash with ActorLogging {
  import DaemonSocketClientActor._
  import context.dispatcher

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
        session.addMessageHandler(classOf[String], new Whole[String] {
          override def onMessage(message: String): Unit = {
            context.parent ! upickle.default.read[ServerToDaemonMessage](message)
          }
        })
        session.addMessageHandler(classOf[Array[Byte]], new Whole[Array[Byte]] {
          override def onMessage(message: Array[Byte]): Unit = {
            context.parent ! GarminImg(message)
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

  def send(session: Session, f: (RemoteEndpoint.Async, SendHandler) => Unit) : Future[Sent.type] = {
    val p = Promise[Sent.type]()
    f(session.getAsyncRemote, new SendHandler {
      override def onResult(result: SendResult): Unit = {
        if (result.isOK) p.success(Sent)
        else p.failure(result.getException)
      }
    })
    p.future
      .recover({case ex => log.error(ex, "error sending msg"); Sent})
  }

  def sendBinary(session: Session, data: ByteBuffer) : Future[Sent.type] =
    send(session, _.sendBinary(data, _))

  def sendText(session: Session, data: String) : Future[Sent.type] =
    send(session, _.sendText(data, _))

  val disconnected : Receive = {
    case Connect(session) =>
      unstashAll()
      context.parent ! DaemonActor.Connected
      context.become(connected(session))
  }


  def connected(session: Session) : Receive = {
    ({
      case msg:DaemonToServerMessage =>
        sendText(session, upickle.default.write(msg))
          .pipeTo(self)
        context.become(sending(session))
      case UploadGpsTrack(data) =>
        sendBinary(session, ByteBuffer.wrap(data))
          .pipeTo(self)
        context.become(sending(session))

      case Disconnect =>
        context.parent ! DaemonActor.Disconnected
        context.become(disconnected)
    }:Receive) orElse disconnected
  }

  def sending(session: Session) : Receive = {
    ({
      case msg:DaemonToServerMessage =>
        stash()
      case msg:UploadGpsTrack =>
        stash()
      case Sent =>
        unstashAll()
        context.become(connected(session))
    }:Receive) orElse connected(session)
  }

  override def receive: Receive = disconnected
}

object DaemonSocketClientActor {
  case class Connect(session: Session)
  object Disconnect
  object Sent
}
