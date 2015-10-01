package hu.mapro.mapping

import java.util.Date

import akka.actor.ActorSystem
import akka.http.scaladsl.model.ws.{BinaryMessage, Message, TextMessage}
import akka.http.scaladsl.server.Directives
import akka.stream.Materializer
import akka.stream.scaladsl.Flow
import akka.stream.stage._
import akka.util.ByteString
import hu.mapro.mapping.Messaging._
import hu.mapro.mapping.actors.MainActor.{GpsTrackUploaded, ToAllClients}

import scala.concurrent.duration._

class Webservice(
  system: ActorSystem,
  materializer: Materializer,
  mappingClients: MappingClients
) extends Directives {
  import system.dispatcher
  implicit  val mat = materializer

  system.scheduler.schedule(15.second, 15.second) {
    mappingClients.injectMessage(ToAllClients(Tick))
  }

  def route =
    path("socket") {
      handleWebsocketMessages(websocketClientFlow())
    }

  def websocketClientFlow(): Flow[Message, Message, Unit] =
    Flow[Message]
      .mapAsync(1)({
//        case msg:BinaryMessage =>
//          msg
//            .dataStream
//            .runFold(ByteString.empty)(_ ++ _)
//            .map(bs => GpsTrackUploaded(bs.toArray))
        case msg:TextMessage =>
          msg
            .textStream
            .runFold("")(_ ++ _)
            .map(pickle.clientToServer(_))
      })
      .via(mappingClients.clientFlow()) // ... and route them through the chatFlow ...
      .map {
        case message:ServerToClientMessage => {
          TextMessage.Strict(pickle.serverToClient(message)) // ... pack outgoing messages into WS JSON messages ...
        }
      }
      .via(reportErrorsFlow) // ... then log any processing errors on stdin

  def reportErrorsFlow[T]: Flow[T, T, Unit] =
    Flow[T]
      .transform(() => new PushStage[T, T] {
        def onPush(elem: T, ctx: Context[T]): SyncDirective = ctx.push(elem)

        override def onUpstreamFailure(cause: Throwable, ctx: Context[T]): TerminationDirective = {
          println(s"WS stream failed with $cause")
          super.onUpstreamFailure(cause, ctx)
        }
      })
}
