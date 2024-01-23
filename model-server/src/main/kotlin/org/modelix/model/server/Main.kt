/*
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *  http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package org.modelix.model.server

import com.beust.jcommander.JCommander
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.call
import io.ktor.server.application.install
import io.ktor.server.engine.embeddedServer
import io.ktor.server.html.respondHtmlTemplate
import io.ktor.server.http.content.resources
import io.ktor.server.http.content.static
import io.ktor.server.netty.Netty
import io.ktor.server.netty.NettyApplicationEngine
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.plugins.cors.routing.CORS
import io.ktor.server.plugins.forwardedheaders.ForwardedHeaders
import io.ktor.server.plugins.swagger.swaggerUI
import io.ktor.server.resources.Resources
import io.ktor.server.response.respondText
import io.ktor.server.routing.IgnoreTrailingSlash
import io.ktor.server.routing.Routing
import io.ktor.server.routing.get
import io.ktor.server.routing.routing
import io.ktor.server.websocket.WebSockets
import io.ktor.server.websocket.pingPeriod
import io.ktor.server.websocket.timeout
import kotlinx.html.a
import kotlinx.html.h1
import kotlinx.html.li
import kotlinx.html.style
import kotlinx.html.ul
import org.apache.commons.io.FileUtils
import org.apache.ignite.Ignition
import org.modelix.authorization.KeycloakUtils
import org.modelix.authorization.installAuthentication
import org.modelix.model.server.handlers.ContentExplorer
import org.modelix.model.server.handlers.DeprecatedLightModelServer
import org.modelix.model.server.handlers.HistoryHandler
import org.modelix.model.server.handlers.KeyValueLikeModelServer
import org.modelix.model.server.handlers.ModelReplicationServer
import org.modelix.model.server.handlers.RepositoriesManager
import org.modelix.model.server.handlers.RepositoryOverview
import org.modelix.model.server.store.IStoreClient
import org.modelix.model.server.store.IgniteStoreClient
import org.modelix.model.server.store.InMemoryStoreClient
import org.modelix.model.server.store.LocalModelClient
import org.modelix.model.server.store.loadDump
import org.modelix.model.server.store.writeDump
import org.modelix.model.server.templates.PageWithMenuBar
import org.slf4j.LoggerFactory
import org.springframework.util.ResourceUtils
import java.io.File
import java.io.IOException
import java.nio.charset.StandardCharsets
import java.time.Duration
import javax.sql.DataSource

object Main {
    private val LOG = LoggerFactory.getLogger(Main::class.java)
    const val DEFAULT_PORT = 28101

    @JvmStatic
    fun main(args: Array<String>) {
        val cmdLineArgs = CmdLineArgs()
        val commander = JCommander(cmdLineArgs)
        commander.parse(*args)

        if (cmdLineArgs.help) {
            commander.usage()
            return
        }

        LOG.info("Max memory (bytes): " + Runtime.getRuntime().maxMemory())
        LOG.info("Server process started")
        LOG.info("In memory: " + cmdLineArgs.inmemory)
        LOG.info("Path to secret file: " + cmdLineArgs.secretFile)
        LOG.info("Path to JDBC configuration file: " + cmdLineArgs.jdbcConfFile)
        LOG.info("Schema initialization: " + cmdLineArgs.schemaInit)
        LOG.info("Set values: " + cmdLineArgs.setValues)
        LOG.info("Disable Swagger-UI: " + cmdLineArgs.noSwaggerUi)

        if (cmdLineArgs.dumpOutName != null && !cmdLineArgs.inmemory) {
            throw RuntimeException("For now dumps are supported only with the inmemory option")
        }
        if (cmdLineArgs.dumpInName != null && !cmdLineArgs.inmemory) {
            throw RuntimeException("For now dumps are supported only with the inmemory option")
        }
        try {
            val portStr = System.getenv("MODELIX_SERVER_PORT")
            var port = portStr?.toInt() ?: DEFAULT_PORT
            if (cmdLineArgs.port != null) {
                port = cmdLineArgs.port!!
            }
            LOG.info("Port: $port")
            val storeClient: IStoreClient
            if (cmdLineArgs.inmemory) {
                if (cmdLineArgs.jdbcConfFile != null) {
                    LOG.warn("JDBC conf file is ignored when in-memory flag is set")
                }
                if (cmdLineArgs.schemaInit) {
                    LOG.warn("Schema initialization is ignored when in-memory flag is set")
                }
                storeClient = InMemoryStoreClient()
                if (cmdLineArgs.dumpInName != null) {
                    val file = File(cmdLineArgs.dumpInName!!)
                    val keys = storeClient.loadDump(file)
                    println("Values loaded from " + file.absolutePath + " (" + keys + ")")
                }
                if (cmdLineArgs.dumpOutName != null) {
                    Runtime.getRuntime()
                        .addShutdownHook(
                            DumpOutThread(
                                storeClient,
                                cmdLineArgs.dumpOutName ?: "dump",
                            ),
                        )
                }
            } else if (cmdLineArgs.localPersistence) {
                storeClient = IgniteStoreClient(cmdLineArgs.jdbcConfFile, inmemory = true)
            } else {
                storeClient = IgniteStoreClient(cmdLineArgs.jdbcConfFile)
                if (cmdLineArgs.schemaInit) {
                    val dataSource: DataSource = Ignition.loadSpringBean<DataSource>(
                        Main::class.java.getResource("ignite.xml"),
                        "dataSource",
                    )
                    SqlUtils(dataSource.connection).ensureSchemaInitialization()
                }
            }
            var i = 0
            while (i < cmdLineArgs.setValues.size) {
                storeClient.put(cmdLineArgs.setValues[i], cmdLineArgs.setValues[i + 1])
                i += 2
            }
            val localModelClient = LocalModelClient(storeClient)
            val repositoriesManager = RepositoriesManager(localModelClient)
            val modelServer = KeyValueLikeModelServer(repositoriesManager)
            val sharedSecretFile = cmdLineArgs.secretFile
            if (sharedSecretFile.exists()) {
                modelServer.setSharedSecret(
                    FileUtils.readFileToString(sharedSecretFile, StandardCharsets.UTF_8),
                )
            }
            val jsonModelServer = DeprecatedLightModelServer(localModelClient)
            val repositoryOverview = RepositoryOverview(repositoriesManager)
            val historyHandler = HistoryHandler(localModelClient, repositoriesManager)
            val contentExplorer = ContentExplorer(localModelClient, repositoriesManager)
            val modelReplicationServer = ModelReplicationServer(repositoriesManager)
            val ktorServer: NettyApplicationEngine = embeddedServer(Netty, port = port) {
                install(Routing)
                installAuthentication(unitTestMode = !KeycloakUtils.isEnabled())
                install(ForwardedHeaders)
                install(Resources)
                // https://opensource.zalando.com/restful-api-guidelines/#136
                install(IgnoreTrailingSlash)
                install(WebSockets) {
                    pingPeriod = Duration.ofSeconds(30)
                    timeout = Duration.ofSeconds(30)
                    maxFrameSize = Long.MAX_VALUE
                    masking = false
                }
                install(ContentNegotiation) {
                    json()
                }
                install(CORS) {
                    anyHost()
                    allowHeader(HttpHeaders.ContentType)
                    allowMethod(HttpMethod.Options)
                    allowMethod(HttpMethod.Get)
                    allowMethod(HttpMethod.Put)
                    allowMethod(HttpMethod.Post)
                }

                modelServer.init(this)
                historyHandler.init(this)
                repositoryOverview.init(this)
                contentExplorer.init(this)
                jsonModelServer.init(this)
                modelReplicationServer.init(this)
                routing {
                    static("/public") {
                        resources("public")
                    }
                    get("/") {
                        call.respondHtmlTemplate(PageWithMenuBar("root", ".")) {
                            headContent {
                                style {
                                    +"""
                                    body {
                                        font-family: sans-serif;
                                    table {
                                        border-collapse: collapse;
                                    }
                                    td, th {
                                        border: 1px solid #888;
                                        padding: 3px 12px;
                                    }
                                    """.trimIndent()
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
                        call.respondText("Model Server")
                    }
                    if (cmdLineArgs.noSwaggerUi) {
                        get("swagger") {
                            call.respondText("SwaggerUI is disabled")
                        }
                    } else {
                        // we serve the public API to the outside via swagger UI
                        swaggerUI(path = "swagger", swaggerFile = ResourceUtils.getFile("api/model-server.yaml").path.toString())
                    }
                }
            }
            ktorServer.start(wait = true)
            LOG.info("Server started")
            Runtime.getRuntime().addShutdownHook(
                Thread {
                    try {
                        ktorServer.stop()
                        LOG.info("Server stopped")
                    } catch (ex: Exception) {
                        LOG.error("", ex)
                    }
                },
            )
        } catch (ex: Exception) {
            LOG.error("", ex)
        }
    }

    private class DumpOutThread internal constructor(storeClient: IStoreClient, dumpName: String) :
        Thread(
            Runnable {
                try {
                    storeClient.writeDump(File(dumpName))
                    println("[Saved memory store into $dumpName]")
                } catch (e: IOException) {
                    e.printStackTrace()
                }
            },
        )
}
