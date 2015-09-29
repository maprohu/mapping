import com.typesafe.sbt.packager.archetypes.JavaAppPackaging
import com.typesafe.sbt.web.SbtWeb
import com.typesafe.sbt.web.Import.WebKeys._
import spray.revolver.RevolverPlugin.Revolver
import scala.collection.JavaConversions._

val webAssetsBase = SettingKey[File]("web-assets-base", "The directory where web assets are written")
val webAssetsPath = SettingKey[String]("web-assets-path", "The path within the web-assets-base where assets are written")
val webAssetsTarget = SettingKey[File]("web-assets-target", "The directory where web asset files will be written")

lazy val commonSettings = Seq(
  version := "0.1-SNAPSHOT",
  scalaVersion := "2.11.7"
)

lazy val root = project.in(file(".")).
  aggregate(appJS, appJVM).
  settings(
    publish := {},
    publishLocal := {}
  )



lazy val app = crossProject.in(file("app"))
  .settings(net.virtualvoid.sbt.graph.Plugin.graphSettings:_*)
  .settings(commonSettings:_*)
  .configure(Deps.appDependencies)
  .jvmSettings(Revolver.settings: _*)
  .jvmSettings(
    resolvers ++= Deps.appJVMResolvers,
    libraryDependencies ++= Deps.appJVMDependencies,

    // use mkgmap to generate style for garmin maps
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

    requiresDOM := true,

    // generataing public resources in a directory so they can be
    // added to runtime classpath to be served by jvm process
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
  .dependsOn(api)
  .configs()
  .settings(
    // adding the fully optimized JS to the prodcution build
    mappings in (Compile, packageBin) ++= (
      (webJars in (appJS, Assets)).value ++
      Seq(
        (fastOptJS in (appJS, Compile)).value.data,
        (fullOptJS in (appJS, Compile)).value.data,
        (packageScalaJSLauncher in (appJS, Compile)).value.data,
        (packageJSDependencies in (appJS, Compile)).value
      )
    ) pair relativeTo((webAssetsBase in appJS).value),

    // using the output of fastOptJs as resources at runtime
    (fullClasspath in Runtime) += (webAssetsBase in appJS).value

  )


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
  .configure(Deps.appJSDependencies)

lazy val api = project
  .settings(commonSettings:_*)
  .settings(
    libraryDependencies ++= Deps.apiDependencies
  )


lazy val daemon = project
  .settings(Revolver.settings: _*)
  .settings(commonSettings:_*)
  .settings(
    libraryDependencies ++= Deps.daemonDependencies
  )
  .dependsOn(api)



// library dependencies in git submodule
lazy val leaflet = ProjectRef(file("scalajs-facades"), "leaflet")

lazy val leafletDraw = ProjectRef(file("scalajs-facades"), "leafletDraw")

lazy val leafletContextmenu = ProjectRef(file("scalajs-facades"), "leafletContextmenu")

lazy val sidebarV2 = ProjectRef(file("scalajs-facades"), "sidebarV2")

lazy val pouchdb = ProjectRef(file("scalajs-facades"), "pouchdb")

lazy val bootstrapNotify = ProjectRef(file("scalajs-facades"), "bootstrapNotify")
