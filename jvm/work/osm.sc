import java.io.{FileInputStream, File}

import com.garmin.fit._
import hu.mapro.mapping.Fit

import scala.xml.XML

object MS {

  val xml = XML.load(getClass.getResource("/monsanto.osm.xml"))

  val highwayTags = (xml \ "way" \ "tag")
    .filter(tag => (tag \ "@k").text == "highway")
    .map(tag => tag \ "@v")
    .toSet

}

object WriteFit {

  def wf = {
    //val records = Fit.readRecords(getClass.getResource("/test01.fit"))
    val out = new FileEncoder(new java.io.File("target/testcourse_course.fit"))

    val inputStream = new FileInputStream("jvm/src/main/resources/course01.fit")
    new Decode().read(
      inputStream,
      new MesgListener {
        override def onMesg(mesg: Mesg): Unit = {
          mesg.getNum match {
            case MesgNum.COURSE =>
              val cm = new CourseMesg(mesg)
              cm.setName("testcourse")
              out.write(cm)

            case _ =>
              out.write(mesg)
          }
        }
      }

    )
    //records.take(100).foreach( out.write(_) )


    inputStream.close()
    out.close()
  }

}


/*
never:
- motorway
- motorway_link,
- trunk,
- trunk_link,
- primary,
- primary_link,
- steps,

link:
- secondary,
- secondary_link,
- tertiary,
- tertiary_link,
- unclassified
- residential,
- service,
- living_street,
- pedestrian,
- track
- road
- footway
- path,

must:
- cycleway,
 */
