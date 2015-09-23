import sbt._
import Keys._

object HelloBuild extends Build {
  val img = taskKey[Unit]("Generates IMG")

  override def settings: Seq[Def.Setting[_]] = super.settings ++ Seq(
    img := println("hello world!")
  )
}