package hu.mapro.mapping

import scala.xml.XML

/**
 * Created by pappmar on 17/09/2015.
 */
object MS {

  lazy val xml = XML.load(getClass.getResource("/monsanto.osm.xml"))

  lazy val highwayTags = (xml \ "way" \ "tag")
    .filter(tag => (tag \ "@k").text == "highway")
    .map(tag => (tag \ "@v").text)
    .toSet
    .toList
    .sorted
}
