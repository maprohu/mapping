package hu.mapro.mapping.actors

import java.net.URLEncoder

import akka.actor.{Actor, ActorLogging, ActorRef, Stash}
import akka.http.scaladsl.Http
import akka.http.scaladsl.model._
import akka.http.scaladsl.unmarshalling.Unmarshaller
import akka.pattern._
import akka.stream.ActorMaterializer
import hu.mapro.mapping.Messaging._
import hu.mapro.mapping._
import hu.mapro.mapping.actors.MainActor._
import hu.mapro.mapping.api.DaemonApi.RequestGarminImg
import rapture.json.Json
import rapture.json.jsonBackends.json4s._
import reactivemongo.api._
import reactivemongo.api.collections.bson.BSONCollection
import reactivemongo.bson.{BSONDocument, BSONHandler, _}

import scala.concurrent.Future
import scala.util.{Properties, Random}

/**
 * Created by marci on 26-09-2015.
 */

class OSMActor(db: ActorRef) extends Actor with Stash with ActorLogging {
  import OSMActor._
  import context.dispatcher

  implicit val coordinatesHandler: BSONHandler[BSONDocument, Coordinates] =
    Macros.handler[Coordinates]
  implicit val cyclewaysHandler: BSONHandler[BSONDocument, Cycleways] =
    Macros.handler[Cycleways]
//  implicit val cyclewaysDBHandler: BSONHandler[BSONDocument, CyclewaysDB] =
//    Macros.handler[CyclewaysDB]
  implicit val cyclewaysDBReader = Macros.reader[CyclewaysDB]
  implicit val cyclewaysDBWriter = Macros.writer[CyclewaysDB]
  implicit val aoiDBReader = Macros.reader[AoiDB]
  implicit val aoiDBWriter = Macros.writer[AoiDB]

  val coll:BSONCollection = {
    val mongoUri = Properties.envOrElse("MONGOLAB_URI", "mongodb://localhost/mapping")
    val parsedUri = MongoConnection.parseURI(mongoUri).get
    val driver = new MongoDriver
    val connection = driver.connection(parsedUri)
    val db = connection(parsedUri.db.get)
    db.collection("osm")
  }

  val loadCycleways = coll
    .find(
      BSONDocument("_id" -> Ids.cycleways.toString)
    )
    .cursor[CyclewaysDB]()
    .headOption

  for {
    (cycleways) <-
      for {
        cycleways <- loadCycleways
      } yield (cycleways)
  } {
    self ! DataLoaded(cycleways.map(_.cycleways))
  }

  def receive = {
    case DataLoaded(cycleways) =>
      unstashAll()
      context.become(working(cycleways))
    case _ => stash()
  }


  def working(cycleways: Option[Cycleways]) : Receive = {
    case FetchCycleways(polygon) =>
      fetchCyclewaysOSM(polygon)
        .map(CyclewaysFetched(_))
        .pipeTo(self)
      
    case CyclewaysFetched(newCycleways) =>
      log.debug("Processing cycleways...")
      coll.update(
        BSONDocument("_id" -> Ids.cycleways.toString),
        CyclewaysDB(newCycleways),
        upsert = true
      )
      context.parent ! ToAllClients(CyclewaysChanged(newCycleways))
      context.become(working(Some(newCycleways)))


    case NewClient(client) =>
      cycleways.foreach(
        client ! CyclewaysChanged(_)
      )

    case RequestGarminImg(_) =>
      db.ask()

  }

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
    log.debug("Querying OSM...")
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
        log.debug("Parsing OSM response...")
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

        val cws = ways
          .map { way =>
            way.nodes.as[Seq[Long]].map(nodeMap)
          }

        Cycleways(cws, polygon)

      }).apply(res.entity)
    }

  }

}

object OSMActor {
  case class CyclewaysFetched(cycleways: Cycleways)
  case class DataLoaded(cycleways: Option[Cycleways])

  object Ids extends Enumeration {
    val cycleways = Value
  }

}

case class CyclewaysDB(cycleways: Cycleways)
case class AoiDB(aoi: Polygon)
