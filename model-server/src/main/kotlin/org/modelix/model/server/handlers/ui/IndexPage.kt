package org.modelix.model.server.handlers.ui

import io.ktor.server.application.Application
import io.ktor.server.application.call
import io.ktor.server.html.respondHtmlTemplate
import io.ktor.server.routing.get
import io.ktor.server.routing.routing
import kotlinx.html.a
import kotlinx.html.h1
import kotlinx.html.li
import kotlinx.html.style
import kotlinx.html.ul
import kotlinx.html.unsafe
import org.modelix.model.server.templates.PageWithMenuBar

/**
 * Landing page of the model-server with links to other pages.
 */
class IndexPage {

    fun init(application: Application) {
        application.routing {
            get("/") {
                call.respondHtmlTemplate(PageWithMenuBar("root", ".")) {
                    headContent {
                        style {
                            unsafe {
                                raw(
                                    """
                                            body {
                                                font-family: sans-serif;
                                            table {
                                                border-collapse: collapse;
                                            }
                                            td, th {
                                                border: 1px solid #888;
                                                padding: 3px 12px;
                                            }
                                    """.trimIndent(),
                                )
                            }
                        }
                    }
                    bodyContent {
                        h1 { +"Model Server" }
                        ul {
                            li {
                                a("repos/") { +"View Repositories on the Model Server" }
                            }
                            li {
                                a("json/") { +"JSON API for JavaScript clients" }
                            }
                            li {
                                a("headers") { +"View HTTP headers" }
                            }
                            li {
                                a("user") { +"View JWT token and permissions" }
                            }
                            li {
                                a("swagger") { +"SwaggerUI" }
                            }
                        }
                    }
                }
            }
        }
    }
}
