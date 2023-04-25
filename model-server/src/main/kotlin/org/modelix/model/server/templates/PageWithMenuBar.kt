package org.modelix.model.server.templates

import io.ktor.server.html.*
import kotlinx.html.*

class PageWithMenuBar(val activePage: String, val baseUrl: String) : Template<HTML> {

    val headContent = Placeholder<HEAD>()
    val bodyContent = Placeholder<FlowContent>()

    override fun HTML.apply() {
        head {
            link("$baseUrl/public/modelix-base.css", rel="stylesheet")
            link("$baseUrl/public/menu-bar.css", rel="stylesheet")
            insert(headContent)
        }
        body {
            div("menu") {
                a("$baseUrl/../", classes="logo") {
                    img("Modelix Logo") {
                        src = "$baseUrl/public/logo-dark.svg"
                        width = "70px"
                        height = "70px"
                    }
                }
                val menuItems = mapOf("history/" to "History",
                    "content/" to "Content",
                    "json/" to "JSON API",
                    "headers" to "HTTP Headers",
                    "user" to "JWT token and permissions")
                var classes = "menuItem"
                if (activePage == "root") {
                    classes += " menuItemActive"
                }
                div(classes) {
                    a(href="$baseUrl/") { +"Model Server"}
                }
                for (entry in menuItems) {
                    var entryClasses = "menuItem"
                    if (activePage == entry.key) {
                        entryClasses += " menuItemActive"
                    }
                    div(entryClasses) {
                        a(href="$baseUrl/${entry.key}") { +entry.value }
                    }
                }
            }
            div {
                style = "display: flex; flex-direction: column; align-items: center;"
                insert(bodyContent)
            }
        }
    }
}
