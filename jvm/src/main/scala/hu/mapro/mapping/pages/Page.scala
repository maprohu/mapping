package hu.mapro.mapping.pages

import scalatags.Text.all._

object Page{
  val skeleton =
    html(
      head(
        link(
          rel:="stylesheet",
          href:="main.css"
        )
      ),
      body(
        div(id := "map")
        , script(src:="mapping-jsdeps.js")
        , script(src:="mapping-fastopt.js")
        , script(src:="mapping-launcher.js")
      )
    )
}