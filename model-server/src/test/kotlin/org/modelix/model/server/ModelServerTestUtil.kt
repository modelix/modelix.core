package org.modelix.model.server

import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.Application
import io.ktor.server.application.install
import io.ktor.server.application.pluginOrNull
import io.ktor.server.engine.EmbeddedServer
import io.ktor.server.engine.connector
import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty
import io.ktor.server.netty.NettyApplicationEngine
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.resources.Resources
import io.ktor.server.routing.IgnoreTrailingSlash
import io.ktor.server.testing.ApplicationTestBuilder
import io.ktor.server.websocket.WebSockets
import kotlinx.coroutines.runBlocking
import org.modelix.authorization.ModelixAuthorization
import org.modelix.model.client2.ModelClientV2
import org.modelix.model.server.Main.installStatusPages
import org.modelix.model.server.handlers.Paths.registerJsonTypes

suspend fun ApplicationTestBuilder.createModelClient(): ModelClientV2 {
    val url = "http://localhost/v2"
    return ModelClientV2.builder().url(url).client(client).build().also { it.init() }
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

/**
 * Allow running a model server for test with Netty.
 *
 * This allows testing properties that would not be testable with [io.ktor.server.testing.testApplication]
 * because the requests run on proper request threads.
 *
 * Examples for such properties are
 * (1) errors while writing responses and
 * (2) effects of blocking code on request threads.
 */
fun runWithNettyServer(
    setupBlock: (application: Application) -> Unit,
    testBlock: suspend (server: EmbeddedServer<NettyApplicationEngine, NettyApplicationEngine.Configuration>) -> Unit,
) {
    val nettyServer = embeddedServer(Netty, configure = {
        connector { this.port = 0 }
        responseWriteTimeoutSeconds = 30
    }) {
        installDefaultServerPlugins()
        setupBlock(this)
    }

    try {
        nettyServer.start(wait = false)
        runBlocking {
            testBlock(nettyServer)
        }
    } finally {
        nettyServer.stop()
    }
}
