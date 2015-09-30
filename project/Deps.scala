import org.scalajs.sbtplugin.ScalaJSPlugin.autoImport._
import org.scalajs.sbtplugin.cross.CrossProject
import sbt.Keys._
import sbt._

object Deps {

  val appJVMResolvers = Seq(
    "cwatch-ext-release" at "http://cwatch.org/repo/ext-release-local",
    Resolver.sonatypeRepo("snapshots"),
    Resolver.typesafeRepo("releases")
  )



  val appJVMDependencies = Seq(
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
    "com.propensive" %% "rapture-json" % "2.0.0-M2-SNAPSHOT",
    "com.propensive" %% "rapture-json-json4s" % "2.0.0-M2-SNAPSHOT",
    "org.reactivemongo" %% "reactivemongo" % "0.11.7"
  )

  val appDependencies = (p:CrossProject) => {p.settings(libraryDependencies ++= Seq(
    "com.lihaoyi" %%% "scalatags" % "0.5.2",
    "com.lihaoyi" %%% "upickle" % "0.3.6",
    "com.lihaoyi" %%% "autowire" % "0.2.5",
    "com.softwaremill.macwire" %% "macros" % "2.0.0"
  ))}

  val appJSDependencies = (p:Project) => {p.settings(
    libraryDependencies ++= Seq(
      "org.scala-js" %%% "scalajs-dom" % "0.8.0",
      "org.scala-lang.modules" %% "scala-async" % "0.9.5",
      "org.webjars" % "font-awesome" % "4.4.0",
      "be.doeraene" %%% "scalajs-jquery" % "0.8.0",
      "com.lihaoyi" %%% "scalarx" % "0.2.8",
      "org.monifu" %%% "monifu" % "1.0-RC3"
    ),
    jsDependencies ++= Seq(
      "org.webjars" % "bootstrap" % "3.3.5" / "js/bootstrap.js" dependsOn "jquery.js"
    )
  )}

  val daemonDependencies = Seq(
    "com.github.kxbmap" %% "configs" % "0.2.5",
    "com.typesafe" % "config" % "1.3.0",
    "org.monifu" %% "monifu" % "1.0-RC3",
    "org.glassfish.tyrus.bundles" % "tyrus-standalone-client" % "1.12",
    "com.typesafe.akka" %% "akka-actor" % "2.3.14",
    "com.softwaremill.macwire" %% "macros" % "2.0.0",
    "com.lihaoyi" %% "upickle" % "0.3.6"


  )
  val apiDependencies = Seq(
    "com.jsuereth" %% "scala-arm" % "1.4"

  )

}