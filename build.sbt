import com.typesafe.sbt.web.SbtWeb
import com.typesafe.sbt.web.Import.WebKeys._
import NativePackagerKeys._

name := "Mapping"


lazy val root = project.in(file(".")).
  aggregate(appJS, appJVM).
  settings(
    publish := {},
    publishLocal := {}
  )


lazy val app = crossProject.in(file(".")).
  settings(
    name := "mapping",
    version := "0.1-SNAPSHOT",
    scalaVersion := "2.11.7",
    libraryDependencies ++= Seq(
      "com.lihaoyi" %%% "scalatags" % "0.5.2",
      "com.lihaoyi" %%% "upickle" % "0.3.6",
      "com.lihaoyi" %%% "autowire" % "0.2.5"
    )
  )
  .jvmSettings(Revolver.settings: _*)
  .jvmSettings(
    resolvers ++= Seq(
      "cwatch-ext-release" at "http://cwatch.org/repo/ext-release-local"
    ),
    //credentials += Credentials(Path.userHome / ".ivy2" / "cwatch.credentials"),
    libraryDependencies ++= Seq(
      "io.spray" %% "spray-can" % "1.3.2",
      "io.spray" %% "spray-routing" % "1.3.2",
      "com.typesafe.akka" %% "akka-actor" % "2.3.6",
      "com.garmin" % "fit" % "16.10",
      "com.jsuereth" %% "scala-arm" % "1.4"
    ),
    cancelable in Global := true
  ).
  jsSettings(
    persistLauncher in Compile := true,
    persistLauncher in Test := false,

    libraryDependencies ++= Seq(
      "org.scala-js" %%% "scalajs-dom" % "0.8.0"
      //, "com.github.sjsf" %%% "sjsf-leaflet" % "0.0.1-SNAPSHOT"
    ),
    requiresDOM := true

  )

lazy val appJVM = app.jvm
  .enablePlugins(JavaServerAppPackaging)
  .settings(
    (resourceDirectories in Compile) += (webJarsDirectory in (appJS, Assets)).value,
    (resourceGenerators in Compile) <+= (webJars in (appJS, Assets)),
    (resources in Compile) ++= Seq(
      (fastOptJS in (appJS, Compile)).value.data
      , (packageScalaJSLauncher in (appJS, Compile)).value.data
      , (packageJSDependencies in (appJS, Compile)).value
    )

  )

lazy val leaflet = ProjectRef(file("scalajs-facades"), "leaflet")

lazy val appJS = app.js
  .enablePlugins(SbtWeb)
  .dependsOn(leaflet)

