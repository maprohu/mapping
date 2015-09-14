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
      "com.lihaoyi" %%% "scalatags" % "0.4.6",
      "com.lihaoyi" %%% "upickle" % "0.2.7"
    )
  ).
  jvmSettings(
    resolvers ++= Seq(
      "cwatch-priv-release" at "https://cwatch.org/repo/priv-release-local"
    ),
    credentials += Credentials(Path.userHome / ".ivy2" / "cwatch.credentials"),
    libraryDependencies ++= Seq(
      "io.spray" %% "spray-can" % "1.3.2",
      "io.spray" %% "spray-routing" % "1.3.2",
      "com.typesafe.akka" %% "akka-actor" % "2.3.6",
      "com.garmin" % "fit" % "16.10",
      "com.jsuereth" %% "scala-arm" % "1.4"
    )
  ).
  jsSettings(
    persistLauncher in Compile := true,
    persistLauncher in Test := false,

    libraryDependencies ++= Seq(
      "org.scala-js" %%% "scalajs-dom" % "0.8.0"
    )
  )

lazy val appJVM = app.jvm.settings(
  (resources in Compile) ++= Seq(
    (fastOptJS in (appJS, Compile)).value.data
    , (packageScalaJSLauncher in (appJS, Compile)).value.data
    , (packageJSDependencies in (appJS, Compile)).value
  )
)
lazy val appJS = app.js

