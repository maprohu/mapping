package hu.mapro.mapping.actors

import java.net.URLEncoder

import akka.actor.{Stash, Actor}
import akka.http.scaladsl.Http
import akka.http.scaladsl.model._
import akka.http.scaladsl.unmarshalling.Unmarshaller
import com.mongodb.{casbah, BasicDBList, DBObject}
import com.mongodb.casbah.Imports._
import hu.mapro.mapping.actors.MainActor._
import hu.mapro.mapping.{Geo, Coordinates}
import hu.mapro.mapping.Messaging._
import rapture.json.Json
import rapture.json.jsonBackends.json4s._

import scala.concurrent.Future
import scala.util.{Random, Properties}
import com.mongodb.casbah.commons.Implicits._
import akka.stream.ActorMaterializer

import akka.pattern._
import scala.collection.JavaConversions._

/**
 * Created by marci on 26-09-2015.
 */

class OSMActor extends Actor {
  import OSMActor._

  // FIXME use reactive mongo api
  val mongoUri = Properties.envOrElse("MONGOLAB_URI", "mongodb://localhost/mapping")

  private val mongoClientURI: casbah.MongoClientURI = MongoClientURI(mongoUri)
  val mongoClient = MongoClient(mongoClientURI)

  val db = mongoClient(mongoClientURI.database.get)

  val coll = db("osm")





  def receive = {
    val result = coll.findOneByID(Ids.cycleways.toString)
    result
      .filter(_.containsField("data"))
      .map { cycleways =>
        cycleways.get("data").asInstanceOf[BasicDBList].toSeq.map { cycleway =>
          cycleway.asInstanceOf[BasicDBList].toSeq.map { coordinates =>
            val c = coordinates.asInstanceOf[DBObject]
            Coordinates(
              c.get("lat").asInstanceOf[Double],
              c.get("lon").asInstanceOf[Double]
            )
          }
        }
      }
      .map(working(_))
      .getOrElse(
        working()
      )
  }

  def withCommon(custom:Receive) : Receive = custom.orElse({
    case FetchCycleways(polygon) =>
      fetchCyclewaysOSM(polygon)
        .map(CyclewaysLoaded(_))
        .pipeTo(self)
    case CyclewaysLoaded(cycleways) =>
      coll.update(
        MongoDBObject("_id" -> Ids.cycleways.toString),
        $set("data" -> MongoDBList(
          cycleways
            .map({ cycleway =>
              MongoDBList(
                cycleway
                  .map({ coordinates =>
                    MongoDBObject(
                      "lat" -> coordinates.lat,
                      "lon" -> coordinates.lon
                    )
                  })
                  :_*
              )
            })
            :_*
        )),
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

//  def cyclewaysRequestPayload(polygon: Polygon) = {
//    val bounds = Geo.bounds(polygon)
//    <osm-script output="json">
//      <query type="way">
//        <has-kv k="highway" v="cycleway"/>
//        <bbox-query
//        s={bounds.minlat.toString}
//        w={bounds.minlon.toString}
//        n={bounds.maxlat.toString}
//        e={bounds.maxlon.toString}
//        />
//      </query>
//      <union>
//        <item />
//        <recurse type="way-node"/>
//      </union>
//      <print mode="skeleton"/>
//    </osm-script>
//  }
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
  implicit val dispatcher = context.dispatcher

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
  case class CyclewaysLoaded(cycleways: Cycleways)

  object Ids extends Enumeration {
    val cycleways = Value
  }

}

case class CyclewaysDB(_id: String, cycleways: Cycleways)
