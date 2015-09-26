import com.typesafe.sbt.packager.archetypes.JavaAppPackaging
import com.typesafe.sbt.web.SbtWeb
import com.typesafe.sbt.web.Import.WebKeys._
import spray.revolver.RevolverPlugin.Revolver
import scala.collection.JavaConversions._

val webAssetsBase = SettingKey[File]("web-assets-base", "The directory where web assets are written")
val webAssetsPath = SettingKey[String]("web-assets-path", "The path within the web-assets-base where assets are written")
val webAssetsTarget = SettingKey[File]("web-assets-target", "The directory where web asset files will be written")

name := "Mapping"


lazy val root = project.in(file(".")).
  aggregate(appJS, appJVM).
  settings(
    publish := {},
    publishLocal := {}
  )


lazy val app = crossProject.in(file("."))
  .settings(net.virtualvoid.sbt.graph.Plugin.graphSettings:_*)
  .settings(
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
      "cwatch-ext-release" at "http://cwatch.org/repo/ext-release-local" ,
      Resolver.sonatypeRepo("snapshots")
    ),
    //credentials += Credentials(Path.userHome / ".ivy2" / "cwatch.credentials"),
    libraryDependencies ++= Deps.serverDependencies,
    cancelable in Global := true,
    resourceGenerators in Compile += Def.task {
      uk.me.parabola.mkgmap.main.Main.mainNoSystemExit(
        Seq(
          s"--output-dir=${(resourceManaged in Compile).value.absolutePath}",
          (baseDirectory.value / "src" / "script" / "garmin" / "typ" / "mapstyle.txt").absolutePath
        ).toArray
      )
      Seq((resourceManaged in Compile).value / "mapstyle.typ")
    }.taskValue,
    resourceGenerators in Compile += Def.task {
      val zipFile = (resourceManaged in Compile).value / "mapstyle.zip"
      val styleDir = baseDirectory.value / "src" / "script" / "garmin" / "style"
      IO.zip((styleDir ** "*") filter {!_.isDirectory} pair relativeTo(styleDir), zipFile)
      Seq(zipFile)
    }.taskValue

  ).
  jsSettings(
    persistLauncher in Compile := true,
    persistLauncher in Test := false,

    libraryDependencies ++= Seq(
      "org.scala-js" %%% "scalajs-dom" % "0.8.0",
      "com.softwaremill.macwire" %% "macros" % "2.0.0",
      "org.scala-lang.modules" %% "scala-async" % "0.9.5",
      "org.webjars" % "font-awesome" % "4.4.0",
      "be.doeraene" %%% "scalajs-jquery" % "0.8.0",
      "com.lihaoyi" %%% "scalarx" % "0.2.8"
    ),
    jsDependencies ++= Seq(
      "org.webjars" % "bootstrap" % "3.3.5" / "js/bootstrap.js" dependsOn "jquery.js"
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

lazy val leafletDraw = ProjectRef(file("scalajs-facades"), "leafletDraw")

lazy val leafletContextmenu = ProjectRef(file("scalajs-facades"), "leafletContextmenu")

lazy val sidebarV2 = ProjectRef(file("scalajs-facades"), "sidebarV2")

lazy val pouchdb = ProjectRef(file("scalajs-facades"), "pouchdb")

lazy val bootstrapNotify = ProjectRef(file("scalajs-facades"), "bootstrapNotify")

lazy val appJS = app.js
  .enablePlugins(SbtWeb)
  .dependsOn(
    leaflet,
    leafletDraw,
    pouchdb,
    sidebarV2,
    leafletContextmenu,
    bootstrapNotify
  )

