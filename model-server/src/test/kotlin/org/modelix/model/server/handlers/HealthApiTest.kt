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
import io.ktor.http.HttpStatusCode
import io.ktor.server.routing.routing
import io.ktor.server.testing.ApplicationTestBuilder
import io.ktor.server.testing.testApplication
import io.mockk.clearMocks
import io.mockk.every
import io.mockk.spyk
import kotlinx.serialization.json.Json
import org.junit.jupiter.api.Test
import org.modelix.authorization.installAuthentication
import org.modelix.model.InMemoryModels
import org.modelix.model.server.installDefaultServerPlugins
import org.modelix.model.server.store.InMemoryStoreClient
import org.modelix.model.server.store.LocalModelClient
import org.modelix.model.server.store.forGlobalRepository
import kotlin.test.AfterTest
import kotlin.test.assertEquals

class HealthApiTest {
    private val inMemoryModels = InMemoryModels()
    private val store = InMemoryStoreClient().forGlobalRepository()
    private val localModelClient = LocalModelClient(store)
    private val repositoriesManager = RepositoriesManager(localModelClient)
    private val healthApi = HealthApiImpl(repositoriesManager, localModelClient.store, inMemoryModels)
    private val healthApiSpy = spyk(healthApi, recordPrivateCalls = true)

    private fun runApiTest(block: suspend ApplicationTestBuilder.() -> Unit) = testApplication {
        application {
            installAuthentication(unitTestMode = true)
            installDefaultServerPlugins()
            routing {
                healthApiSpy.installRoutes(this)
            }
        }

        block()
    }

    @AfterTest
    fun resetSpyK() {
        clearMocks(healthApiSpy)
    }

    @Test
    fun `health endpoint returns healthy`() = runApiTest {
        val response = client.get("/health")
        assertEquals(HttpStatusCode.OK, response.status)
        assertEquals("healthy", response.bodyAsText())
    }

    @Test
    fun `health endpoint returns not healthy if unhealthy`() = runApiTest {
        every { healthApiSpy["isHealthy"]() } returns false
        val response = client.get("/health")
        assertEquals(HttpStatusCode.InternalServerError, response.status)

        val expectedProblem = HttpException(HttpStatusCode.InternalServerError, details = "not healthy").problem
        assertEquals(expectedProblem, Json.decodeFromString<org.modelix.api.v1.Problem>(response.bodyAsText()))
    }
}
