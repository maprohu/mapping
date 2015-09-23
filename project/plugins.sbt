logLevel := Level.Warn

addSbtPlugin("org.scala-js" % "sbt-scalajs" % "0.6.5")

addSbtPlugin("io.spray" % "sbt-revolver" % "0.7.1")

addSbtPlugin("com.typesafe.sbt" % "sbt-web" % "1.2.2")

addSbtPlugin("com.typesafe.sbt" % "sbt-native-packager" % "1.0.3")

resolvers ++= Seq(
  "cwatch-ext-release" at "http://cwatch.org/repo/ext-release-local"
)

libraryDependencies ++= Seq(
  "uk.me.parabola" % "mkgmap" % "r3643"
)