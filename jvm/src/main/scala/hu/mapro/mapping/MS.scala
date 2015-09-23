package hu.mapro.mapping

import scala.xml.{Node, Elem, NodeSeq, XML}

/**
 * Created by pappmar on 17/09/2015.
 */
object MS {

  lazy val xml : Elem = XML.load(getClass.getResource("/monsanto.osm.xml"))

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

  val isCycleway: (Node) => Boolean = way => (way \ "tag")
    .filter(tag => (tag \ "@k").text == "highway")
    .headOption
    .exists(tag => (tag \ "@v").text == "cycleway")
  
  def cycleways[W, N](nodeTrf: (NodeSeq => N))(wayTrf: ((NodeSeq, Seq[N]) => W)) : Seq[W] =
      (xml \ "way")
      .filter { isCycleway }
      .map { way =>
        wayTrf(
          way,
          (way \ "nd")
            .map( nd => nodeTrf( nodeMap((nd \ "@ref").text.toLong )) )
        )
      }

  lazy val cycleWayNodeIds : Set[Long] =
    ((xml \ "way").filter { isCycleway } \ "nd" map { nd => (nd \ "@ref").text.toLong }).toSet

  lazy val cycleXml =
    xml.copy(
      child = xml.child flatMap {
        case elem:Elem =>
          elem.label match {
            case "node" if !(cycleWayNodeIds contains (elem \ "@id").text.toLong) => None
            case "relation" => None
            case "way" if !isCycleway(elem) => None
            case _ => Some(elem.copy())
          }
        case _ => None
      }
    )





}
