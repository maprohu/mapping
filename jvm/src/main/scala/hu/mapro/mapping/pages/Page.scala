package hu.mapro.mapping.pages

import scalatags.Text.all._

object Page{
  val fast = index("fastopt")
  val full = index("opt")
  def index(opt: String) =
    html(
      head(
        link(
          rel:="stylesheet",
          href:= "main.css"
        )
      ),
      body(
        div(id := "map")
        , script(src:="mapping-jsdeps.js")
        , script(src:=s"mapping-$opt.js")
        , script(src:="mapping-launcher.js")
      )
    )
}