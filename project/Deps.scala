import sbt._

object Deps {


  val serverDependencies = Seq(
    "com.typesafe.akka" %% "akka-http-experimental" % "1.0",
    "org.scala-lang.modules" % "scala-xml_2.11" % "1.0.5",
    //      "io.spray" %% "spray-can" % "1.3.2",
    //      "io.spray" %% "spray-routing" % "1.3.2",
    //      "com.typesafe.akka" %% "akka-actor" % "2.3.6",
    "com.garmin" % "fit" % "16.10",
    "com.jsuereth" %% "scala-arm" % "1.4",
    "com.vividsolutions" % "jts" % "1.13",
    "uk.me.parabola" % "mkgmap" % "r3643",
    "com.typesafe.slick" %% "slick" % "3.0.3",
    "org.postgresql" % "postgresql" % "9.4-1203-jdbc42",
    "com.google.guava" % "guava" % "18.0",
    "org.slf4j" % "slf4j-simple" % "1.6.4",
    "com.softwaremill" %% "akka-http-session" % "0.1.4",
    "org.mongodb" %% "casbah" % "2.8.2",
    "com.propensive" %% "rapture-json" % "2.0.0-M2-SNAPSHOT",
    "com.propensive" %% "rapture-json-json4s" % "2.0.0-M2-SNAPSHOT"
  )
}