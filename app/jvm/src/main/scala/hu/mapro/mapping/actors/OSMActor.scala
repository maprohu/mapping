package hu.mapro.mapping.actors

import java.net.URLEncoder

import akka.actor.{Actor, Stash}
import akka.http.scaladsl.Http
import akka.http.scaladsl.model._
import akka.http.scaladsl.unmarshalling.Unmarshaller
import akka.pattern._
import akka.stream.ActorMaterializer
import hu.mapro.mapping.Coordinates
import hu.mapro.mapping.Messaging._
import hu.mapro.mapping.actors.MainActor._
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

class OSMActor extends Actor with Stash {
  import OSMActor._
  import context.dispatcher

  implicit val coordinatesHandler: BSONHandler[BSONDocument, Coordinates] =
    Macros.handler[Coordinates]
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
  val loadAOI = coll
    .find(
      BSONDocument("_id" -> Ids.aoi.toString)
    )
    .cursor[AoiDB]()
    .headOption

  for {
    (cycleways, aoi) <-
      for {
        cycleways <- loadCycleways
        aoi <- loadAOI
      } yield (cycleways, aoi)
  } {
    self ! DataLoaded(cycleways.map(_.cycleways), aoi.map(_.aoi))
  }

  def receive = {
    case DataLoaded(cycleways, aoi) =>
      unstashAll()
      context.become(working(cycleways, aoi))
    case _ => stash()
  }


  def working(cycleways: Option[Cycleways], aoi: Option[Polygon]) : Receive = {
    case FetchCycleways(polygon) =>
      fetchCyclewaysOSM(polygon)
        .map(CyclewaysFetched(_))
        .pipeTo(self)
      
    case CyclewaysFetched(newCycleways) =>
      coll.update(
        BSONDocument("_id" -> Ids.cycleways.toString),
        CyclewaysDB(newCycleways),
        upsert = true
      )
      context.parent ! ToAllClients(CyclewaysChanged(newCycleways))
      context.become(working(Some(newCycleways), aoi))

    case UpdateAOI(newAoi) =>
      coll.update(
        BSONDocument("_id" -> Ids.aoi.toString),
        AoiDB(newAoi),
        upsert = true
      )
      context.parent ! ToAllClients(AOIUpdated(newAoi))
      context.become(working(cycleways, Some(newAoi)))

    case NewClient(client) =>
      cycleways.foreach(
        client ! CyclewaysChanged(_)
      )
      aoi.foreach(
        client ! AOIUpdated(_)
      )
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
  case class CyclewaysFetched(cycleways: Cycleways)
  case class DataLoaded(cycleways: Option[Cycleways], aoi: Option[Polygon])

  object Ids extends Enumeration {
    val cycleways = Value
    val aoi = Value
  }

}

case class CyclewaysDB(cycleways: Cycleways)
case class AoiDB(aoi: Polygon)
