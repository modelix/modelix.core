/*
 * Copyright (c) 2024.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.modelix.model.server

import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.Application
import io.ktor.server.application.install
import io.ktor.server.application.pluginOrNull
import io.ktor.server.netty.Netty
import io.ktor.server.netty.NettyApplicationEngine
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.resources.Resources
import io.ktor.server.routing.IgnoreTrailingSlash
import io.ktor.server.testing.ApplicationTestBuilder
import io.ktor.server.websocket.WebSockets
import kotlinx.coroutines.runBlocking
import org.modelix.api.v2.Paths.registerJsonTypes
import org.modelix.authorization.ModelixAuthorization
import org.modelix.authorization.installAuthentication
import org.modelix.model.client2.ModelClientV2
import org.modelix.model.server.Main.installStatusPages

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
    testBlock: suspend (server: NettyApplicationEngine) -> Unit,
) {
    val nettyServer: NettyApplicationEngine = io.ktor.server.engine.embeddedServer(Netty, port = 0) {
        installAuthentication(unitTestMode = true)
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
