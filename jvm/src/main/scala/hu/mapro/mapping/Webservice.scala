package hu.mapro.mapping

import java.util.Date

import akka.actor.ActorSystem
import akka.http.scaladsl.model.ws.{Message, TextMessage}
import akka.http.scaladsl.server.Directives
import akka.stream.Materializer
import akka.stream.scaladsl.Flow
import akka.stream.stage._
import hu.mapro.mapping.MainActor.ToAllClients

import scala.concurrent.duration._

class Webservice(implicit fm: Materializer, system: ActorSystem) extends Directives {
  val theClients = MappingClients.create(system)
  import system.dispatcher
  system.scheduler.schedule(15.second, 15.second) {
    theClients.injectMessage(ToAllClients(Tick))
  }

  def route =
    path("socket") {
      handleWebsocketMessages(websocketClientFlow())
    }

  def websocketClientFlow(): Flow[Message, Message, Unit] =
    Flow[Message]
      .collect {
        case TextMessage.Strict(msg) ⇒ pickle.clientToServer(msg) // unpack incoming WS text messages...
        // This will lose (ignore) messages not received in one chunk (which is
        // unlikely because chat messages are small) but absolutely possible
        // FIXME: We need to handle TextMessage.Streamed as well.
      }
      .via(theClients.clientFlow()) // ... and route them through the chatFlow ...
      .map {
        case message:ServerToClientMessage => {
          TextMessage.Strict(pickle.serverToClient(message)) // ... pack outgoing messages into WS JSON messages ...
        }
      }
      .via(reportErrorsFlow) // ... then log any processing errors on stdin

  def reportErrorsFlow[T]: Flow[T, T, Unit] =
    Flow[T]
      .transform(() ⇒ new PushStage[T, T] {
        def onPush(elem: T, ctx: Context[T]): SyncDirective = ctx.push(elem)

        override def onUpstreamFailure(cause: Throwable, ctx: Context[T]): TerminationDirective = {
          println(s"WS stream failed with $cause")
          super.onUpstreamFailure(cause, ctx)
        }
      })
}
