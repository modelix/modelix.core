package org.modelix.model.server.templates

import io.ktor.server.html.*
import kotlinx.html.*
import java.security.KeyStore.Entry

class PageWithMenuBar(val activePage: String, val baseUrl: String) : Template<HTML> {

    val headContent = Placeholder<HEAD>()
    val content = Placeholder<FlowContent>()

    override fun HTML.apply() {
        head {
            insert(headContent)
            style {
                unsafe {
                    +"""
                    .menuList {
                        list-style-type: none;
                        margin: 0;
                        padding: 8px;
                        text-align: center;
                        overflow: hidden;
                        background-color: #282828;
                        border-radius: 10px;
                        position: sticky;
                        top: 0;
                        z-index: 2;
                    }
                    .menuItem {
                        float: left;
                        font-family: sans-serif;
                        font-size: 14pt;
                        background-color: #282828;
                        margin-right: 5px;
                    }
                    .menuItem > a {
                        display: block;
                        text-decoration: none;
                        color: #ffffff;
                        text-align: center;
                        padding: 12px 20px;
                        background-color: #282828;
                        border-radius: 10px;
                    }
                    .menuItem > a:hover{
                        background-color: #404040;
                    }
                    .menuItemActive > a {
                        background-color: #6290c3;
                    }  
                    """.trimIndent()
                }
            }
        }
        body {
            val menuItems = mapOf("history/" to "History",
                "content/" to "Content",
                "json/" to "JSON API",
                "headers" to "HTTP Headers",
                "user" to "JWT token and permissions")
            ul("menuList") {
                var classes = "menuItem"
                if (activePage == "root") {
                    classes += " menuItemActive"
                }
                li(classes) {
                    a(href="$baseUrl/") { +"Model Server"}
                }
                for (entry in menuItems) {
                    var entryClasses = "menuItem"
                    if (activePage == entry.key) {
                        entryClasses += " menuItemActive"
                    }
                    li(entryClasses) {
                        a(href="$baseUrl/${entry.key}") { +entry.value }
                    }
                }
            }
            insert(content)
        }
    }

}