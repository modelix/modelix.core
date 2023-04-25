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
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import io.ktor.server.application.*
import io.ktor.server.engine.*
import io.ktor.server.html.*
import io.ktor.server.http.content.*
import io.ktor.server.netty.*
import io.ktor.server.plugins.contentnegotiation.*
import io.ktor.server.plugins.cors.routing.*
import io.ktor.server.plugins.forwardedheaders.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.server.routing.get
import io.ktor.server.websocket.*
import kotlinx.html.*
import org.apache.commons.io.FileUtils
import org.apache.ignite.Ignition
import org.modelix.authorization.KeycloakUtils
import org.modelix.authorization.installAuthentication
import org.modelix.model.server.handlers.*
import org.modelix.model.server.store.IStoreClient
import org.modelix.model.server.store.IgniteStoreClient
import org.modelix.model.server.store.InMemoryStoreClient
import org.modelix.model.server.store.LocalModelClient
import org.modelix.model.server.templates.PageWithMenuBar
import org.slf4j.LoggerFactory
import java.io.File
import java.io.FileReader
import java.io.FileWriter
import java.io.IOException
import java.nio.charset.StandardCharsets
import javax.sql.DataSource

object Main {
    private val LOG = LoggerFactory.getLogger(Main::class.java)
    const val DEFAULT_PORT = 28101

    @JvmStatic
    fun main(args: Array<String>) {
        val cmdLineArgs = CmdLineArgs()
        JCommander(cmdLineArgs).parse(*args)
        LOG.info("Max memory (bytes): " + Runtime.getRuntime().maxMemory())
        LOG.info("Server process started")
        LOG.info("In memory: " + cmdLineArgs.inmemory)
        LOG.info("Path to secret file: " + cmdLineArgs.secretFile)
        LOG.info("Path to JDBC configuration file: " + cmdLineArgs.jdbcConfFile)
        LOG.info("Schema initialization: " + cmdLineArgs.schemaInit)
        LOG.info("Set values: " + cmdLineArgs.setValues)
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
                    val file = File(cmdLineArgs.dumpInName)
                    val keys = storeClient.load(FileReader(file))
                    println(
                        "Values loaded from " + file.absolutePath + " (" + keys + ")"
                    )
                }
                if (cmdLineArgs.dumpOutName != null) {
                    Runtime.getRuntime()
                        .addShutdownHook(
                            DumpOutThread(
                                storeClient,
                                cmdLineArgs.dumpOutName
                            )
                        )
                }
            } else {
                storeClient = IgniteStoreClient(cmdLineArgs.jdbcConfFile)
                if (cmdLineArgs.schemaInit) {
                    val dataSource: DataSource = Ignition.loadSpringBean<DataSource>(
                        Main::class.java.getResource("ignite.xml"), "dataSource"
                    )
                    SqlUtils(dataSource.connection).ensureSchemaInitialization()
                }
            }
            var i = 0
            while (i < cmdLineArgs.setValues.size) {
                storeClient.put(cmdLineArgs.setValues[i], cmdLineArgs.setValues[i + 1],)
                i += 2
            }
            val modelServer = KeyValueLikeModelServer(storeClient)
            val localModelClient = LocalModelClient(storeClient)
            val sharedSecretFile = cmdLineArgs.secretFile
            if (sharedSecretFile.exists()) {
                modelServer.setSharedSecret(
                    FileUtils.readFileToString(sharedSecretFile, StandardCharsets.UTF_8)
                )
            }

            val jsonModelServer = DeprecatedLightModelServer(localModelClient)
            val repositoriesManager = RepositoriesManager(localModelClient)
            val historyHandler = HistoryHandler(localModelClient, repositoriesManager)
            val contentExplorer = ContentExplorer(localModelClient, repositoriesManager)
            val modelReplicationServer = ModelReplicationServer(repositoriesManager)
            val ktorServer: NettyApplicationEngine = embeddedServer(Netty, port = port) {
                install(Routing)
                installAuthentication(unitTestMode = !KeycloakUtils.isEnabled())
                install(ForwardedHeaders)
                install(WebSockets)
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
                                style { +"""
                                    body {
                                        font-family: sans-serif;
                                    table {
                                        border-collapse: collapse;
                                    }
                                    td, th {
                                        border: 1px solid #888;
                                        padding: 3px 12px;
                                    }
                                """.trimIndent() }
                            }
                            bodyContent {
                                h1 { +"Model Server" }
                                ul {
                                    li {
                                        a("history/") { +"Model History" }
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
                                }
                            }
                        }
                        call.respondText("Model Server")
                    }
                }
            }
            ktorServer.start(wait = true)
            LOG.info("Server started")
            Runtime.getRuntime().addShutdownHook(Thread {
                try {
                    ktorServer.stop()
                    LOG.info("Server stopped")
                } catch (ex: Exception) {
                    LOG.error("", ex)
                }
            })
        } catch (ex: Exception) {
            LOG.error("", ex)
        }
    }

    private class DumpOutThread internal constructor(inMemoryStoreClient: InMemoryStoreClient, dumpName: String?) :
        Thread(
            Runnable {
                var fw: FileWriter? = null
                try {
                    fw = FileWriter(File(dumpName))
                    inMemoryStoreClient.dump(fw!!)
                    println("[Saved memory store into $dumpName]")
                } catch (e: IOException) {
                    e.printStackTrace()
                } finally {
                    if (fw != null) {
                        try {
                            fw!!.close()
                        } catch (e: IOException) {
                            e.printStackTrace()
                        }
                    }
                }
            })
}