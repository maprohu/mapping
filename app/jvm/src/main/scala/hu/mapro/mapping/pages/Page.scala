package hu.mapro.mapping.pages

import scalatags.Text.all._

object Page{
  val fast = index("fastopt")
  val full = index("opt")
  def index(opt: String) =
    html(
      head(
        meta(
          name := "viewport",
          content := "width=device-width, initial-scale=1.0, maximum-scale=1.0, user-scalable=no"
        ),
        link(
          rel:="stylesheet",
          href:= "main.css"
        )
      ),
      body(
        script(src:="app-jsdeps.js")
        , script(src:=s"app-$opt.js")
        , script(src:="app-launcher.js")
      )
    )
}