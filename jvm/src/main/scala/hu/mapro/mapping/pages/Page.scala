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
        div(
          id := "sidebar",
          cls := "sidebar collapsed",
          ul(
            cls := "sidebar-tabs",
            role := "tablist",
            li(a(href := "#home", role := "tab", i(cls := "fa fa-bars"))),
            li(a(href := "#profile", role := "tab", i(cls := "fa fa-user"))),
            li(a(href := "#messages", role := "tab", i(cls := "fa fa-envelope"))),
            li(a(href := "#settings", role := "tab", i(cls := "fa fa-gear")))
          ),
          div(
            cls := "sidebar-content active",
            div(cls := "sidebar-pane", id := "home"),
            div(cls := "sidebar-pane", id := "profile"),
            div(cls := "sidebar-pane", id := "messages"),
            div(cls := "sidebar-pane", id := "settings")
          )
        ),
        div(
          id := "map",
          cls := "sidebar-map"
        )
        , script(src:="mapping-jsdeps.js")
        , script(src:=s"mapping-$opt.js")
        , script(src:="mapping-launcher.js")
      )
    )
}