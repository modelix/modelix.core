package org.modelix.model.server

import com.beust.jcommander.JCommander
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.Application
import io.ktor.server.application.ApplicationCall
import io.ktor.server.application.install
import io.ktor.server.application.pluginOrNull
import io.ktor.server.engine.connector
import io.ktor.server.engine.embeddedServer
import io.ktor.server.http.content.staticResources
import io.ktor.server.netty.Netty
import io.ktor.server.plugins.calllogging.CallLogging
import io.ktor.server.plugins.calllogging.processingTimeMillis
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.plugins.cors.routing.CORS
import io.ktor.server.plugins.forwardedheaders.ForwardedHeaders
import io.ktor.server.plugins.origin
import io.ktor.server.plugins.statuspages.StatusPages
import io.ktor.server.plugins.swagger.swaggerUI
import io.ktor.server.request.httpMethod
import io.ktor.server.request.path
import io.ktor.server.resources.Resources
import io.ktor.server.response.respondText
import io.ktor.server.routing.IgnoreTrailingSlash
import io.ktor.server.routing.get
import io.ktor.server.routing.routing
import io.ktor.server.websocket.WebSockets
import io.ktor.server.websocket.pingPeriod
import io.ktor.server.websocket.timeout
import kotlinx.serialization.json.Json
import org.apache.commons.io.FileUtils
import org.apache.ignite.Ignition
import org.modelix.authorization.ModelixAuthorization
import org.modelix.authorization.NoPermissionException
import org.modelix.authorization.NotLoggedInException
import org.modelix.model.server.Main.installStatusPages
import org.modelix.model.server.handlers.AboutApiImpl
import org.modelix.model.server.handlers.HealthApiImpl
import org.modelix.model.server.handlers.HttpException
import org.modelix.model.server.handlers.IdsApiImpl
import org.modelix.model.server.handlers.KeyValueLikeModelServer
import org.modelix.model.server.handlers.LionwebApiImpl
import org.modelix.model.server.handlers.MetricsApiImpl
import org.modelix.model.server.handlers.ModelReplicationServer
import org.modelix.model.server.handlers.Paths.registerJsonTypes
import org.modelix.model.server.handlers.Problem
import org.modelix.model.server.handlers.RepositoriesManager
import org.modelix.model.server.handlers.ui.ContentExplorer
import org.modelix.model.server.handlers.ui.DiffView
import org.modelix.model.server.handlers.ui.HistoryHandler
import org.modelix.model.server.handlers.ui.IndexPage
import org.modelix.model.server.handlers.ui.RepositoryOverview
import org.modelix.model.server.store.IgniteStoreClient
import org.modelix.model.server.store.InMemoryStoreClient
import org.modelix.model.server.store.IsolatingStore
import org.modelix.model.server.store.ObjectInRepository
import org.modelix.model.server.store.RequiresTransaction
import org.modelix.model.server.store.forGlobalRepository
import org.modelix.model.server.store.loadDump
import org.modelix.model.server.store.writeDump
import org.slf4j.LoggerFactory
import org.springframework.util.ResourceUtils
import java.io.File
import java.io.FileReader
import java.io.IOException
import java.nio.charset.StandardCharsets
import java.util.Properties
import javax.sql.DataSource
import kotlin.time.Duration.Companion.seconds

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

        LOG.info("Version: $MODELIX_VERSION")
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
                val jdbcProperties = cmdLineArgs.jdbcConfFile?.let(::readJdbcProperties)
                storeClient = IgniteStoreClient(jdbcProperties, inmemory = true)
            } else {
                val jdbcProperties = cmdLineArgs.jdbcConfFile?.let(::readJdbcProperties)
                storeClient = IgniteStoreClient(jdbcProperties)
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
            @OptIn(RequiresTransaction::class)
            globalStoreClient.getTransactionManager().runWrite {
                while (i < cmdLineArgs.setValues.size) {
                    globalStoreClient.put(cmdLineArgs.setValues[i], cmdLineArgs.setValues[i + 1])
                    i += 2
                }
            }
            val repositoriesManager = RepositoriesManager(storeClient)
            val modelServer = KeyValueLikeModelServer(repositoriesManager)
            val sharedSecretFile = cmdLineArgs.secretFile
            if (sharedSecretFile.exists()) {
                modelServer.setSharedSecret(
                    FileUtils.readFileToString(sharedSecretFile, StandardCharsets.UTF_8),
                )
            }
            val repositoryOverview = RepositoryOverview(repositoriesManager)
            val historyHandler = HistoryHandler(repositoriesManager)
            val diffView = DiffView(repositoriesManager)
            val contentExplorer = ContentExplorer(repositoriesManager)
            val modelReplicationServer = ModelReplicationServer(repositoriesManager)
            val metricsApi = MetricsApiImpl()

            val ktorServer = embeddedServer(Netty, configure = {
                connector { this.port = port }
                responseWriteTimeoutSeconds = cmdLineArgs.responseWriteTimeoutSeconds
            }) {
                install(ModelixAuthorization) {
                    permissionSchema = ModelServerPermissionSchema.SCHEMA
                    installStatusPages = false
                    accessControlPersistence = DBAccessControlPersistence(
                        storeClient,
                        ObjectInRepository.global(RepositoriesManager.KEY_PREFIX + ":access-control-data"),
                    )
                }
                install(ForwardedHeaders)
                install(CallLogging) {
                    format { call ->
                        // Resemble the default format but include remote host and user agent for easier tracing on who issued a certain request.
                        // INFO  ktor.application - 200 OK: GET - /public/modelix-base.css in 60ms
                        val status = call.response.status()
                        val httpMethod = call.request.httpMethod.value
                        val userAgent = call.request.headers["User-Agent"]
                        val processingTimeMillis = call.processingTimeMillis()
                        val path = call.request.path()
                        val remoteHost = call.request.origin.remoteHost
                        "$status: $httpMethod - $path in ${processingTimeMillis}ms [Remote host: '$remoteHost', User agent: '$userAgent']"
                    }
                }
                install(Resources)
                // https://opensource.zalando.com/restful-api-guidelines/#136
                install(IgnoreTrailingSlash)
                install(WebSockets) {
                    pingPeriod = 30.seconds
                    timeout = 30.seconds
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
                    allowHeader(HttpHeaders.Authorization)
                    allowMethod(HttpMethod.Options)
                    allowMethod(HttpMethod.Get)
                    allowMethod(HttpMethod.Put)
                    allowMethod(HttpMethod.Post)
                }
                installStatusPages()

                modelServer.init(this)
                IndexPage().init(this)
                historyHandler.init(this)
                diffView.init(this)
                repositoryOverview.init(this)
                contentExplorer.init(this)
                modelReplicationServer.init(this)
                metricsApi.init(this)
                IdsApiImpl(repositoriesManager).init(this)

                routing {
                    HealthApiImpl(repositoriesManager).installRoutes(this)
                    AboutApiImpl.installRoutes(this)
                    if (System.getenv("MODELIX_LIONWEB_API_ENABLED").toBoolean()) {
                        LionwebApiImpl(repositoriesManager).installRoutes(this)
                    }
                    staticResources("/public", "public")

                    if (cmdLineArgs.noSwaggerUi) {
                        get("swagger") {
                            call.respondText("SwaggerUI is disabled")
                        }
                    } else {
                        // We serve the public API to the outside via swagger UI.
                        swaggerUI(path = "swagger", swaggerFile = ResourceUtils.getFile("api/model-server.yaml").invariantSeparatorsPath)
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

    private fun readJdbcProperties(jdbcConfFile: File): Properties {
        val properties = Properties()
        try {
            properties.load(FileReader(jdbcConfFile))
        } catch (e: IOException) {
            throw IllegalStateException("Could not load the JDBC configuration from ${jdbcConfFile.absolutePath}.", e)
        }
        return properties
    }

    /**
     * Installs the status pages extension with a configuration suitable for generating application/problem+json
     * responses as defined in the API specification.
     */
    fun Application.installStatusPages() {
        val problemJsonContentType = ContentType.parse("application/problem+json")

        install(StatusPages) {
            suspend fun ApplicationCall.respondProblem(problem: Problem) {
                val status = problem.status
                requireNotNull(status) { "Status code must exist as it is use for the HTTP response" }

                // No easy way found to override the content type when directly responding with serializable objects.
                respondText(
                    Json.encodeToString(problem),
                    problemJsonContentType,
                    HttpStatusCode.fromValue(status),
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

fun Application.installDefaultServerPlugins(unitTestMode: Boolean = true) {
    install(WebSockets)
    install(ContentNegotiation) {
        json()
        registerJsonTypes()
    }
    install(Resources)
    install(IgnoreTrailingSlash)
    installStatusPages()
    if (pluginOrNull(ModelixAuthorization) == null) {
        install(ModelixAuthorization) {
            if (unitTestMode) configureForUnitTests()
            permissionSchema = ModelServerPermissionSchema.SCHEMA
        }
    }
}
