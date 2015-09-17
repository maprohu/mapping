package hu.mapro.mapping

import scala.xml.{NodeSeq, XML}

/**
 * Created by pappmar on 17/09/2015.
 */
object MS {

  lazy val xml = XML.load(getClass.getResource("/monsanto.osm.xml"))

  lazy val highwayTags =
    (xml \ "way" \ "tag")
      .filter(tag => (tag \ "@k").text == "highway")
      .map(tag => (tag \ "@v").text)
      .toSet
      .toList
      .sorted

  lazy val nodeMap : Map[Long, NodeSeq] =
    (xml \ "node")
      .map(node => (node \ "@id").text.toLong -> node)(collection.breakOut)

  def cycleways[W, N](nodeTrf: (NodeSeq => N))(wayTrf: ((NodeSeq, Seq[N]) => W)) : Seq[W] =
    (xml \ "way")
      .filter { way =>
        (way \ "tag")
          .filter( tag => (tag \ "@k").text == "highway")
          .headOption
          .exists( tag => (tag \ "@v").text == "cycleway")
      }
      .map { way =>
        wayTrf(
          way,
          (way \ "nd")
            .map( nd => nodeTrf( nodeMap((nd \ "@ref").text.toLong )) )
        )
      }





}
