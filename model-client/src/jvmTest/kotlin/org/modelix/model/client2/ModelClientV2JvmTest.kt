package org.modelix.model.client2

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respondError
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class ModelClientV2JvmTest {

    @Test
    fun `Java implementation implements closable functionality`() = runTest {
        val url = "http://localhost/v2"
        val mockEngine = MockEngine {
            respondError(HttpStatusCode.NotFound)
        }
        val httpClient = HttpClient(mockEngine)
        val modelClient = ModelClientV2.builder()
            .client(httpClient)
            .url(url)
            .build()
        // Implementing `close` allow to use `.use` method.
        modelClient.use {
        }
        val firstException = assertFailsWith<CancellationException> {
            modelClient.init()
        }
        assertEquals("Parent job is Completed", firstException.message)
        // `Closable` implies that `.close` method is idempotent.
        modelClient.close()
        val secondException = assertFailsWith<CancellationException> {
            modelClient.init()
        }
        assertEquals("Parent job is Completed", secondException.message)
    }
}
