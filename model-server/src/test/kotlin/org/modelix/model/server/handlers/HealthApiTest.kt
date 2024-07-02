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

package org.modelix.model.server.handlers

import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import io.ktor.server.routing.routing
import io.ktor.server.testing.ApplicationTestBuilder
import io.ktor.server.testing.testApplication
import org.junit.jupiter.api.Test
import org.modelix.authorization.installAuthentication
import org.modelix.model.InMemoryModels
import org.modelix.model.server.installDefaultServerPlugins
import org.modelix.model.server.store.InMemoryStoreClient
import org.modelix.model.server.store.LocalModelClient
import org.modelix.model.server.store.forGlobalRepository
import kotlin.test.assertEquals

class HealthApiTest {

    private fun runTest(block: suspend ApplicationTestBuilder.() -> Unit) = testApplication {
        val inMemoryModels = InMemoryModels()
        val store = InMemoryStoreClient().forGlobalRepository()
        val localModelClient = LocalModelClient(store)
        val repositoriesManager = RepositoriesManager(localModelClient)

        application {
            installAuthentication(unitTestMode = true)
            installDefaultServerPlugins()
            routing {
                HealthApiImpl(repositoriesManager, localModelClient.store, inMemoryModels).installRoutes(this)
            }
        }

        block()
    }

    @Test
    fun `health endpoint returns healthy`() = runTest {
        val response = client.get("/health")
        assertEquals("healthy", response.bodyAsText())
    }
}
