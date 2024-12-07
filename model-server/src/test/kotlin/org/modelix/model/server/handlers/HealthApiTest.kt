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
import org.modelix.model.server.installDefaultServerPlugins
import org.modelix.model.server.store.InMemoryStoreClient
import kotlin.test.AfterTest
import kotlin.test.assertEquals

class HealthApiTest {
    private val store = InMemoryStoreClient()
    private val healthApi = HealthApiImpl(RepositoriesManager(store))
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
        assertEquals(expectedProblem, Json.decodeFromString<Problem>(response.bodyAsText()))
    }
}
