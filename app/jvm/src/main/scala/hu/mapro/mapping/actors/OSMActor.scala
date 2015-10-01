package hu.mapro.mapping.actors

import java.net.URLEncoder

import akka.actor.{Actor, ActorLogging, ActorRef, Stash}
import akka.http.scaladsl.Http
import akka.http.scaladsl.model._
import akka.http.scaladsl.unmarshalling.Unmarshaller
import akka.pattern._
import akka.stream.ActorMaterializer
import akka.util.Timeout
import hu.mapro.mapping.Messaging._
import hu.mapro.mapping._
import hu.mapro.mapping.actors.DBActor.GetAllTracks
import hu.mapro.mapping.actors.MainActor._
import hu.mapro.mapping.api.DaemonApi.{GarminImg, GarminImgUpToDate, RequestGarminImg}
import hu.mapro.mapping.api.Util
import rapture.json.Json
import rapture.json.jsonBackends.json4s._
import reactivemongo.api._
import reactivemongo.api.collections.bson.BSONCollection
import reactivemongo.bson.{BSONDocument, BSONHandler, _}

import scala.concurrent.Future
import scala.concurrent.duration._
import scala.util.{Properties, Random}

/**
 * Created by marci on 26-09-2015.
 */

class OSMActor(db: ActorRef) extends Actor with Stash with ActorLogging {
  import OSMActor._
  import context.dispatcher

  val mkgmap = new Mkgmap(context.system)

  implicit val coordinatesHandler: BSONHandler[BSONDocument, Coordinates] =
    Macros.handler[Coordinates]
  implicit val cyclewaysHandler: BSONHandler[BSONDocument, Cycleways] =
    Macros.handler[Cycleways]
//  implicit val cyclewaysDBHandler: BSONHandler[BSONDocument, CyclewaysDB] =
//    Macros.handler[CyclewaysDB]
  implicit val cyclewaysDBReader = Macros.reader[CyclewaysDB]
  implicit val cyclewaysDBWriter = Macros.writer[CyclewaysDB]

  implicit val timeout = Timeout(5 seconds)

  val coll:BSONCollection = {
    val mongoUri = Properties.envOrElse("MONGOLAB_URI", "mongodb://localhost/mapping")
    val parsedUri = MongoConnection.parseURI(mongoUri).get
    val driver = new MongoDriver
    val connection = driver.connection(parsedUri)
    val db = connection(parsedUri.db.get)
    db.collection("osm")
  }

  private val cyclewaysQuery = BSONDocument("_id" -> Ids.cycleways.toString)
  val loadCycleways = coll
    .find(
      cyclewaysQuery
    )
    .cursor[CyclewaysDB]()
    .headOption
    .recover {
    case ex => log.error(ex, "failed loading cycleways"); None
    }


  for {
    cycleways <- loadCycleways
  } {
    self ! DataLoaded(cycleways.map(_.cycleways))
  }

  def receive = {
    case DataLoaded(cycleways) =>
      log.debug("Cycleways loaded from database.")
      unstashAll()
      context.become(working(cycleways, None))
    case _ => stash()
  }


  def working(cycleways: Option[Cycleways], imgHash: Option[String]) : Receive = {
    case FetchCycleways(polygon) =>
      log.debug("Fetch cycleways requested")
      fetchCyclewaysOSM(polygon)
        .map(CyclewaysFetched(_))
        .pipeTo(self)
      
    case CyclewaysFetched(newCycleways) =>
      log.debug("Processing cycleways...")
      coll.update(
        cyclewaysQuery,
        CyclewaysDB(newCycleways),
        upsert = true
      ).recoverWith({case ex =>
        log.error(ex, "Error saving cycleways")

        coll.remove(
          cyclewaysQuery
        ).flatMap{case y =>
          coll.update(
            cyclewaysQuery,
            CyclewaysDB(newCycleways),
            upsert = true
          )
        }
      }).onComplete(log.debug("Result of saving cycleways: {}", _))
      context.parent ! ToAllClients(CyclewaysChanged(newCycleways))
      context.become(working(Some(newCycleways), None))


    case NewClient(client) =>
      cycleways.foreach(
        client ! CyclewaysChanged(_)
      )

    case RequestGarminImg(reqHash) =>
      log.debug("Garming IMG requested: {}", reqHash)
      if (reqHash.isEmpty || imgHash != reqHash) {
        val replyTo = sender()
        for {
          cw <- cycleways
          tracks <- (db ? GetAllTracks).mapTo[Seq[Track]]
          Some(img) <- mkgmap.generateImg(tracks, cw.bounds)
        } {
          self ! GarminImgGenerated(img.read(), replyTo)
        }
      } else {
        log.info("Garmin IMG file of requestor is up to date: {}", reqHash)
        sender() ! GarminImgUpToDate
      }

    case GarminImgGenerated(data, requestor) =>
      log.info("Sending generated Garming IMG file to requestor.")
      requestor ! GarminImg(data)
      context.become(working(cycleways, Some(Util.hash(data))))

    case GpsTracksChanged =>
      context.become(working(cycleways, None))

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
  case class GarminImgGenerated(data: Array[Byte], requestor: ActorRef)

  object Ids extends Enumeration {
    val cycleways = Value
  }

}

case class CyclewaysDB(cycleways: Cycleways)
