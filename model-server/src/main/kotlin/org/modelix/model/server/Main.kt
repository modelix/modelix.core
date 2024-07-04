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
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.Application
import io.ktor.server.application.ApplicationCall
import io.ktor.server.application.call
import io.ktor.server.application.install
import io.ktor.server.engine.embeddedServer
import io.ktor.server.http.content.staticResources
import io.ktor.server.netty.Netty
import io.ktor.server.netty.NettyApplicationEngine
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.plugins.cors.routing.CORS
import io.ktor.server.plugins.forwardedheaders.ForwardedHeaders
import io.ktor.server.plugins.statuspages.StatusPages
import io.ktor.server.plugins.swagger.swaggerUI
import io.ktor.server.resources.Resources
import io.ktor.server.response.respondRedirect
import io.ktor.server.response.respondText
import io.ktor.server.routing.IgnoreTrailingSlash
import io.ktor.server.routing.Routing
import io.ktor.server.routing.get
import io.ktor.server.routing.routing
import io.ktor.server.websocket.WebSockets
import io.ktor.server.websocket.pingPeriod
import io.ktor.server.websocket.timeout
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.apache.commons.io.FileUtils
import org.apache.ignite.Ignition
import org.modelix.api.v1.Problem
import org.modelix.api.v2.Paths.registerJsonTypes
import org.modelix.authorization.KeycloakUtils
import org.modelix.authorization.NoPermissionException
import org.modelix.authorization.NotLoggedInException
import org.modelix.authorization.installAuthentication
import org.modelix.model.InMemoryModels
import org.modelix.model.server.handlers.DeprecatedLightModelServer
import org.modelix.model.server.handlers.HealthApiImpl
import org.modelix.model.server.handlers.HttpException
import org.modelix.model.server.handlers.IdsApiImpl
import org.modelix.model.server.handlers.KeyValueLikeModelServer
import org.modelix.model.server.handlers.MetricsApiImpl
import org.modelix.model.server.handlers.ModelReplicationServer
import org.modelix.model.server.handlers.RepositoriesManager
import org.modelix.model.server.handlers.ui.ContentExplorer
import org.modelix.model.server.handlers.ui.HistoryHandler
import org.modelix.model.server.handlers.ui.IndexPage
import org.modelix.model.server.handlers.ui.RepositoryOverview
import org.modelix.model.server.store.IgniteStoreClient
import org.modelix.model.server.store.InMemoryStoreClient
import org.modelix.model.server.store.IsolatingStore
import org.modelix.model.server.store.LocalModelClient
import org.modelix.model.server.store.forContextRepository
import org.modelix.model.server.store.forGlobalRepository
import org.modelix.model.server.store.loadDump
import org.modelix.model.server.store.writeDump
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

        LOG.info("Max memory (bytes): ${Runtime.getRuntime().maxMemory()}")
        LOG.info("Server process started")
        LOG.info("In memory: ${cmdLineArgs.inmemory}")
        LOG.info("Path to secret file: ${cmdLineArgs.secretFile}")
        LOG.info("Path to JDBC configuration file: ${cmdLineArgs.jdbcConfFile}")
        LOG.info("Schema initialization: ${cmdLineArgs.schemaInit}")
        LOG.info("Set values: ${cmdLineArgs.setValues}")
        LOG.info("Disable Swagger-UI: ${cmdLineArgs.noSwaggerUi}")
        LOG.info("Response write timeout seconds: ${cmdLineArgs.responseWriteTimeoutSeconds}")

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
            val storeClient: IsolatingStore
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
            val globalStoreClient = storeClient.forGlobalRepository()
            while (i < cmdLineArgs.setValues.size) {
                globalStoreClient.put(cmdLineArgs.setValues[i], cmdLineArgs.setValues[i + 1])
                i += 2
            }
            val localModelClient = LocalModelClient(storeClient.forContextRepository())
            val inMemoryModels = InMemoryModels()
            val repositoriesManager = RepositoriesManager(localModelClient)
            val modelServer = KeyValueLikeModelServer(repositoriesManager, globalStoreClient, inMemoryModels)
            val sharedSecretFile = cmdLineArgs.secretFile
            if (sharedSecretFile.exists()) {
                modelServer.setSharedSecret(
                    FileUtils.readFileToString(sharedSecretFile, StandardCharsets.UTF_8),
                )
            }
            val jsonModelServer = DeprecatedLightModelServer(localModelClient, repositoriesManager)
            val repositoryOverview = RepositoryOverview(repositoriesManager)
            val historyHandler = HistoryHandler(localModelClient, repositoriesManager)
            val contentExplorer = ContentExplorer(localModelClient, repositoriesManager)
            val modelReplicationServer = ModelReplicationServer(repositoriesManager, localModelClient, inMemoryModels)
            val metricsApi = MetricsApiImpl()

            val configureNetty: NettyApplicationEngine.Configuration.() -> Unit = {
                this.responseWriteTimeoutSeconds = cmdLineArgs.responseWriteTimeoutSeconds
            }

            val ktorServer: NettyApplicationEngine = embeddedServer(Netty, port = port, configure = configureNetty) {
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
                    registerJsonTypes()
                }
                install(CORS) {
                    anyHost()
                    allowHeader(HttpHeaders.ContentType)
                    allowMethod(HttpMethod.Options)
                    allowMethod(HttpMethod.Get)
                    allowMethod(HttpMethod.Put)
                    allowMethod(HttpMethod.Post)
                }
                installStatusPages()

                modelServer.init(this)
                IndexPage().init(this)
                historyHandler.init(this)
                repositoryOverview.init(this)
                contentExplorer.init(this)
                jsonModelServer.init(this)
                modelReplicationServer.init(this)
                metricsApi.init(this)
                IdsApiImpl(repositoriesManager, localModelClient).init(this)

                routing {
                    HealthApiImpl(repositoriesManager, globalStoreClient, inMemoryModels).installRoutes(this)

                    staticResources("/public", "public")

                    if (cmdLineArgs.noSwaggerUi) {
                        get("swagger") {
                            call.respondText("SwaggerUI is disabled")
                        }
                    } else {
                        // We serve the public API to the outside via swagger UI.
                        // The ktor swagger plugin currently has no way to serve multiple specifications. Therefore, we
                        // simply offer two versions of the UI for now.
                        swaggerUI(path = "swagger/v2", swaggerFile = ResourceUtils.getFile("api/model-server-v2.yaml").invariantSeparatorsPath)
                        swaggerUI(path = "swagger/v1", swaggerFile = ResourceUtils.getFile("api/model-server-v1.yaml").invariantSeparatorsPath)
                        // by default, users should be using v2
                        get("swagger") {
                            call.respondRedirect("swagger/v2", false)
                        }
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

    /**
     * Installs the status pages extension with a configuration suitable for generating application/problem+json
     * responses as defined in the API specification.
     */
    fun Application.installStatusPages() {
        val problemJsonContentType = ContentType.parse("application/problem+json")

        install(StatusPages) {
            suspend fun ApplicationCall.respondProblem(problem: Problem) {
                requireNotNull(problem.status) { "Status code must exist as it is use for the HTTP response" }

                // No easy way found to override the content type when directly responding with serializable objects.
                respondText(
                    Json.encodeToString(problem),
                    problemJsonContentType,
                    HttpStatusCode.fromValue(problem.status),
                )
            }

            exception<Throwable> { call, cause ->
                // Such errors were most likely not caught and logged anywhere else.
                LOG.error("Encountered an internal server error.", cause)
                call.respondProblem(
                    Problem(
                        title = "Internal server error",
                        detail = cause.message,
                        status = HttpStatusCode.InternalServerError.value,
                        type = "/problems/unclassified-internal-server-error",
                    ),
                )
            }

            exception<HttpException> { call, cause ->
                call.respondProblem(cause.problem)
            }

            // Custom authorization exception types
            exception<NoPermissionException> { call, cause ->
                call.respondProblem(
                    Problem(
                        title = "Forbidden",
                        detail = cause.message,
                        status = HttpStatusCode.Forbidden.value,
                        type = "/problems/forbidden",
                    ),
                )
            }
            exception<NotLoggedInException> { call, cause ->
                call.respondProblem(
                    Problem(
                        title = "Unauthorized",
                        detail = cause.message,
                        status = HttpStatusCode.Unauthorized.value,
                        type = "/problems/unauthorized",
                    ),
                )
            }
        }
    }

    private class DumpOutThread(storeClient: IsolatingStore, dumpName: String) :
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
