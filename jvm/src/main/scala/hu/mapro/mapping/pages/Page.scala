package hu.mapro.mapping.pages

import scalatags.Text.all._

object Page{
  val skeleton =
    html(
      head(
        link(
          rel:="stylesheet",
          href:="https://cdnjs.cloudflare.com/ajax/libs/pure/0.5.0/pure-min.css"
        )
      ),
      body(
        script(src:="/mapping-jsdeps.js")
        , script(src:="/mapping-fastopt.js")
        , script(src:="/mapping-launcher.js")
      )
    )
}