package org.modelix.model.server

import io.ktor.server.application.Application
import io.ktor.server.engine.EmbeddedServer
import io.ktor.server.engine.connector
import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty
import io.ktor.server.netty.NettyApplicationEngine
import io.ktor.server.testing.ApplicationTestBuilder
import kotlinx.coroutines.runBlocking
import org.modelix.model.client2.ModelClientV2

suspend fun ApplicationTestBuilder.createModelClient(): ModelClientV2 {
    val url = "http://localhost/v2"
    return ModelClientV2.builder().url(url).client(client).build().also { it.init() }
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
