package hu.mapro.mapping.actors

import java.net.URLEncoder
import play.api.libs.iteratee.Iteratee
import reactivemongo.api._
import reactivemongo.bson._

import akka.actor.{Stash, Actor}
import akka.http.scaladsl.Http
import akka.http.scaladsl.model._
import akka.http.scaladsl.unmarshalling.Unmarshaller
import hu.mapro.mapping.actors.MainActor._
import hu.mapro.mapping.{Geo, Coordinates}
import hu.mapro.mapping.Messaging._
import rapture.json.Json
import rapture.json.jsonBackends.json4s._
import reactivemongo.api.collections.bson.BSONCollection
import reactivemongo.bson.{BSONHandler, BSONDocument}

import scala.concurrent.Future
import scala.util.{Random, Properties}
import akka.stream.ActorMaterializer

import akka.pattern._
import scala.collection.JavaConversions._

/**
 * Created by marci on 26-09-2015.
 */

class OSMActor extends Actor with Stash {
  import OSMActor._
  import context.dispatcher

  implicit val coordinatesHandler: BSONHandler[BSONDocument, Coordinates] =
    Macros.handler[Coordinates]
//  implicit val cyclewaysDBHandler: BSONHandler[BSONDocument, CyclewaysDB] =
//    Macros.handler[CyclewaysDB]
  implicit val cyclewaysDBReader = Macros.reader[CyclewaysDB]
  implicit val cyclewaysDBWriter = Macros.writer[CyclewaysDB]

  val coll:BSONCollection = {
    val mongoUri = Properties.envOrElse("MONGOLAB_URI", "mongodb://localhost/mapping")
    val parsedUri = MongoConnection.parseURI(mongoUri).get
    val driver = new MongoDriver
    val connection = driver.connection(parsedUri)
    val db = connection(parsedUri.db.get)
    db.collection("osm")
  }

  coll
    .find(
      BSONDocument("_id" -> Ids.cycleways.toString)
    )
    .cursor[CyclewaysDB]()
    .headOption
    .map(_.map(cwdb => CyclewaysLoaded(cwdb.cycleways)).getOrElse(CyclewaysNotFound))
    .recover {case _ => CyclewaysNotFound}
    .pipeTo(self)

  def receive = {
    case CyclewaysNotFound =>
      unstashAll()
      context.become(working())
    case CyclewaysLoaded(cycleways) =>
      unstashAll()
      context.become(working(cycleways))
    case _ => stash()
  }

  def withCommon(custom:Receive) : Receive = custom.orElse({
    case FetchCycleways(polygon) =>
      fetchCyclewaysOSM(polygon)
        .map(CyclewaysLoaded(_))
        .pipeTo(self)
    case CyclewaysLoaded(cycleways) =>
      coll.update(
        BSONDocument("_id" -> Ids.cycleways.toString),
        CyclewaysDB(cycleways),
        upsert = true
      )
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
    <osm-script output="json">
      <query type="node">
        <polygon-query bounds={polygon.map(c => s"${c.lat} ${c.lon}").mkString(" ")}/>
      </query>
      <query type="way">
        <recurse type="node-way"/>
        <has-kv k="highway" v="cycleway"/>
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

  def fetchCyclewaysOSM(polygon: Seq[Coordinates]) : Future[Cycleways] = {
    val osmQuery: String = cyclewaysRequestPayload(polygon).toString
    Http().singleRequest(
      HttpRequest(
        uri = overpassServers(Random.nextInt(overpassServers.size)),
        method = HttpMethods.POST,
        entity = HttpEntity(
          ContentType(MediaTypes.`application/x-www-form-urlencoded`),
          s"data=${URLEncoder.encode(osmQuery, HttpCharsets.`UTF-8`.value)}"
        )
      )
    ).flatMap { res =>
      Unmarshaller.byteStringUnmarshaller.mapWithCharset({ (data, charset) =>
        val jsonString:String = data.decodeString(charset.value)
        val doc = Json.parse(jsonString)

        // assuming nodes are followed by ways
        val (nodes, ways) =
          doc
            .elements.as[Seq[Json]]
            .span(_.`type`.as[String] == "node")

        val nodeMap : Map[Long, Coordinates] =
          nodes
            .map({ node =>
              node.id.as[Long] ->
                Coordinates(
                  lat = node.lat.as[Double],
                  lon = node.lon.as[Double]
                )
            })(collection.breakOut)

        ways
          .map { way =>
            way.nodes.as[Seq[Long]].map(nodeMap)
          }

      }).apply(res.entity)
    }

  }

}

object OSMActor {
  object CyclewaysNotFound
  case class CyclewaysLoaded(cycleways: Cycleways)

  object Ids extends Enumeration {
    val cycleways = Value
  }

}

case class CyclewaysDB(cycleways: Cycleways)
