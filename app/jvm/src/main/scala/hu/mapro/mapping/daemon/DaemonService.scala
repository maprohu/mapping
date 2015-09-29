package hu.mapro.mapping.daemon

import java.util.Date

import akka.actor.ActorSystem
import akka.http.scaladsl.model.ws._
import akka.http.scaladsl.server.Directives
import akka.stream.Materializer
import akka.stream.scaladsl.{Sink, Flow}
import akka.stream.stage._
import akka.util.ByteString
import hu.mapro.mapping.MappingClients
import hu.mapro.mapping.Messaging._
import hu.mapro.mapping.actors.MainActor.{GpsTrackUploaded, ToAllClients}
import akka.pattern._
import hu.mapro.mapping.api.DaemonApi.{DaemonToServerMessage, ServerToDaemonMessage}
import scala.concurrent.ExecutionContext.Implicits.global

import scala.concurrent.duration._
/**
 * Created by pappmar on 29/09/2015.
 */
class DaemonService(
  mappingClients: MappingClients,
  materializer: Materializer
) extends Directives {
  implicit val mat = materializer

  def route =
    path("daemon") {
      handleWebsocketMessages(websocketDaemonFlow())
    }

  def websocketDaemonFlow(): Flow[Message, Message, Unit] =
    Flow[Message]
      .mapAsync(1)({
        case msg:BinaryMessage =>
          msg
            .dataStream
            .runFold(ByteString.empty)(_ ++ _)
            .map(bs => GpsTrackUploaded(bs.toArray))
        case msg:TextMessage =>
          msg
            .textStream
            .runFold("")(_ ++ _)
            .map(str => upickle.default.read[DaemonToServerMessage](str))
      })
      .via(mappingClients.daemonFlow()) // ... and route them through the chatFlow ...
      .map {
      case msg:ServerToDaemonMessage => {
        TextMessage.Strict(upickle.default.write(msg)) // ... pack outgoing messages into WS JSON messages ...
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
