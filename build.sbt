import com.typesafe.sbt.packager.archetypes.{JavaAppPackaging, JavaServerAppPackaging}
import com.typesafe.sbt.web.SbtWeb
import com.typesafe.sbt.web.Import.WebKeys._
import spray.revolver.RevolverPlugin.Revolver
import com.typesafe.sbt.packager.universal.UniversalPlugin.autoImport.{stage => stageTask}

val webAssetsBase = SettingKey[File]("web-assets-base", "The directory where web assets are written")
val webAssetsPath = SettingKey[String]("web-assets-path", "The path within the web-assets-base where assets are written")
val webAssetsTarget = SettingKey[File]("web-assets-target", "The directory where web asset files will be written")

lazy val Dev = config("dev")
lazy val jsResources = TaskKey[Seq[File]]("js-resources", "The JS files to be generated")

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
    requiresDOM := true,

    webAssetsBase := target.value / "assets",
    webAssetsPath := "public",
    webAssetsTarget := webAssetsBase.value / webAssetsPath.value,

    (crossTarget in fastOptJS) := webAssetsTarget.value,
    (crossTarget in fullOptJS) := webAssetsTarget.value,
    (crossTarget in packageScalaJSLauncher) := webAssetsTarget.value,
    (crossTarget in packageJSDependencies) := webAssetsTarget.value,

    (webJarsDirectory in Assets) := webAssetsTarget.value,
    webJarsCache in webJars in Assets := target.value / "webjars-main.cache",

    fastOptJS in Compile := {
      (webJars in Assets).value
      (fastOptJS in Compile).value
    }


  )

lazy val appJVM = app.jvm
  .enablePlugins(JavaAppPackaging)
  .configs()
  .settings(
//    (resourceDirectories in Compile) += (webJarsDirectory in (appJS, Assets)).value,
//    (resourceGenerators in Compile) <+= (webJars in (appJS, Assets)),
//
//    (scalaJSStage in (appJS, stageTask)) := FullOptStage,
//
//    (resourceDirectories in (Compile)) += (webAssetsTarget in appJS).value,
//    (jsResources in Global) :=
//      Seq(
//        (fullOptJS in (appJS, Compile)).value.data
//        , (packageJSDependencies in (appJS, Compile)).value
//        , (packageScalaJSLauncher in (appJS, Compile)).value.data
//      ),
//    (jsResources in Dev) :=
//      Seq(
//        (fastOptJS in (appJS, Compile)).value.data
//        , (packageJSDependencies in (appJS, Compile)).value
//        , (packageScalaJSLauncher in (appJS, Compile)).value.data
//      ),
//    (resourceGenerators in Compile) += jsResources.taskValue,

    mappings in (Compile, packageBin) ++= (
      (webJars in (appJS, Assets)).value ++
      Seq(
        (fullOptJS in (appJS, Compile)).value.data,
        (packageScalaJSLauncher in (appJS, Compile)).value.data,
        (packageJSDependencies in (appJS, Compile)).value
      )
    ) pair relativeTo((webAssetsBase in appJS).value),


    (fullClasspath in Runtime) += (webAssetsBase in appJS).value

  )

lazy val leaflet = ProjectRef(file("scalajs-facades"), "leaflet")

lazy val appJS = app.js
  .enablePlugins(SbtWeb)
  .dependsOn(leaflet)

