/*
 * Copyright (c) 2023.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.modelix.model.server.templates

import io.ktor.server.html.Placeholder
import io.ktor.server.html.Template
import io.ktor.server.html.insert
import kotlinx.html.FlowContent
import kotlinx.html.HEAD
import kotlinx.html.HTML
import kotlinx.html.a
import kotlinx.html.body
import kotlinx.html.div
import kotlinx.html.head
import kotlinx.html.img
import kotlinx.html.link
import kotlinx.html.style

class PageWithMenuBar(val activePage: String, val baseUrl: String) : Template<HTML> {

    val headContent = Placeholder<HEAD>()
    val bodyContent = Placeholder<FlowContent>()

    override fun HTML.apply() {
        head {
            link("$baseUrl/public/modelix-base.css", rel = "stylesheet")
            link("$baseUrl/public/menu-bar.css", rel = "stylesheet")
            insert(headContent)
        }
        body {
            div("menu") {
                a("$baseUrl/../", classes = "logo") {
                    img("Modelix Logo") {
                        src = "$baseUrl/public/logo-dark.svg"
                        width = "70px"
                        height = "70px"
                    }
                }
                val menuItems = mapOf(
                    "repos/" to "Repositories",
                    "json/" to "JSON API",
                    "headers" to "HTTP Headers",
                    "user" to "JWT token and permissions",
                )
                var classes = "menuItem"
                if (activePage == "root") {
                    classes += " menuItemActive"
                }
                div(classes) {
                    a(href = "$baseUrl/") { +"Model Server" }
                }
                for (entry in menuItems) {
                    var entryClasses = "menuItem"
                    if (activePage == entry.key) {
                        entryClasses += " menuItemActive"
                    }
                    div(entryClasses) {
                        a(href = "$baseUrl/${entry.key}") { +entry.value }
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
