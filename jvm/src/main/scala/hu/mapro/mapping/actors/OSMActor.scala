package hu.mapro.mapping.actors

import java.net.URLEncoder

import akka.actor.{Stash, Actor}
import akka.http.scaladsl.Http
import akka.http.scaladsl.Http.OutgoingConnection
import akka.http.scaladsl.model._
import akka.http.scaladsl.unmarshalling.Unmarshaller
import akka.stream.scaladsl.{Flow, Sink, Source}
import com.mongodb.DBObject
import com.mongodb.casbah.{MongoClient, MongoClientURI}
import hu.mapro.mapping.{Geo, Coordinates}
import hu.mapro.mapping.MainActor.{ToAllClients, NewClient}
import hu.mapro.mapping.Messaging._
import rapture.json.Json
import rapture.json.jsonBackends.json4s._

import scala.concurrent.Future
import scala.util.parsing.json.JSON
import scala.util.{Random, Properties}
import com.novus.salat._
import com.novus.salat.annotations._
import com.novus.salat.global._
import akka.stream.ActorMaterializer

import scala.xml.XML

/**
 * Created by marci on 26-09-2015.
 */

class OSMActor extends Actor {
  import OSMActor._

  val mongoUri = Properties.envOrElse("MONGOLAB_URI", "mongodb://localhost")

  val mongoClient = MongoClient(MongoClientURI(mongoUri))

  val db = mongoClient("mapping")

  val coll = db("osm")


  object Ids extends Enumeration {
    val cycleways = Value
  }

  case class CyclewaysDB(cycleways: Cycleways, _id: String = Ids.cycleways.toString)


  def receive = {
    val result = coll.findOneByID(Ids.cycleways.toString)
    result
      .map { dbo:DBObject =>
        working(grater[CyclewaysDB].asObject(dbo).cycleways)
      }
      .getOrElse(
        working()
      )
  }

  def withCommon(custom:Receive) : Receive = custom.orElse({
    case LoadCycleways(polygon) =>
      // ...
    case CyclewaysLoaded(cycleways) =>
      context.parent ! ToAllClients(CyclewaysChanged(cycleways))
      context.become(working(cycleways))
  })

  def working() : Receive = withCommon({
    case NewClient(client) =>
  })

  def working(cycleways: Cycleways) : Receive = withCommon({
    case NewClient(client) =>
      client ! CyclewaysChanged(cycleways)

  })

  val overpassServers = Vector(
    "http://overpass.osm.rambler.ru/cgi/interpreter",
    "http://overpass-api.de/api/interpreter"
  )

  def cyclewaysRequestPayload(polygon: Polygon) = {
    val bounds = Geo.bounds(polygon)
    <osm-script output="json">
      <query type="way">
        <has-kv k="highway" v="cycleway"/>
        <bbox-query
          s={bounds.minlat.toString}
          w={bounds.minlon.toString}
          n={bounds.maxlat.toString}
          e={bounds.maxlon.toString}
        />
      </query>
      <union>
        <item />
        <recurse type="way-node"/>
      </union>
      <print mode="skeleton"/>
    </osm-script>
  }

  implicit val actorSystem = context.system
  implicit val materializer = ActorMaterializer()
  implicit val dispatcher = context.dispatcher

  def fetchCyclewaysOSM(polygon: Seq[Coordinates]) : Future[Cycleways] = {
    Http().singleRequest(
      HttpRequest(
        uri = overpassServers(Random.nextInt(overpassServers.size)),
        method = HttpMethods.POST,
        entity = HttpEntity(
          ContentType(MediaTypes.`application/x-www-form-urlencoded`),
          s"data=${URLEncoder.encode(cyclewaysRequestPayload(polygon).toString, HttpCharsets.`UTF-8`.value)}"
        )
      )
    ).flatMap { res =>
      Unmarshaller.byteStringUnmarshaller.mapWithCharset({ (data, charset) =>
        val doc = Json.parse(data.decodeString(charset.value))

        // FIXME split elements to node and way, knowing that they come in this order
        val nodeMap : Map[Long, Coordinates] =
          doc
            .elements.as[Seq[Json]]
            .filter(_.`type` == "node")
            .map({ node =>
              node.id.as[Long] ->
                Coordinates(
                  lat = node.lat.as[Double],
                  lon = node.lon.as[Double]
                )
            })(collection.breakOut)

        doc
          .elements.as[Seq[Json]]
          .filter(_.`type` == "way")
          .map { way =>
            way.nodes.as[Seq[Long]].map(nodeMap)
          }

      }).apply(res.entity)
    }

  }

}

object OSMActor {
  case class CyclewaysLoaded(cycleways: Cycleways)

}
