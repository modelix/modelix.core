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

package org.modelix.model.client2

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.engine.mock.respondError
import io.ktor.client.plugins.ServerResponseException
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest
import org.modelix.model.lazy.RepositoryId
import org.modelix.model.server.api.v2.VersionDeltaStreamV2
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class ModelClientV2Test {

    @Test
    fun disposeClient() = runTest {
        val url = "http://localhost/v2"
        val mockEngine = MockEngine {
            respondError(HttpStatusCode.NotFound)
        }
        val httpClient = HttpClient(mockEngine)
        val modelClient = ModelClientV2.builder()
            .client(httpClient)
            .url(url)
            .build()
        modelClient.close()
        val exception = assertFailsWith<CancellationException> {
            modelClient.init()
        }
        assertEquals("Parent job is Completed", exception.message)
    }

    @Test
    fun detectIncompleteDataInVersionDeltaStreamV2() = runTest {
        val incompleteData = "CTVRw*a6KXJ4o7uzGlp-kUosxpyRf4f"
        val repositoryId = RepositoryId("aRepositoryId")
        val branchRef = repositoryId.getBranchReference("main")
        val url = "http://localhost/v2"
        val mockEngine = MockEngine { requestData ->
            assertEquals(VersionDeltaStreamV2.CONTENT_TYPE.toString(), requestData.headers[HttpHeaders.Accept])
            respond(incompleteData, HttpStatusCode.OK, headersOf(HttpHeaders.ContentType, VersionDeltaStreamV2.CONTENT_TYPE.toString()))
        }
        val httpClient = HttpClient(mockEngine)
        val modelClient = ModelClientV2.builder()
            .client(httpClient)
            .url(url)
            .build()

        assertFailsWith<VersionDeltaStreamV2.Companion.IncompleteData> {
            modelClient.pull(branchRef, null)
        }
    }

    @Test
    fun retriesCanBeConfigured() = runTest {
        val url = "http://localhost/v2"
        val mockEngine = MockEngine {
            respondError(HttpStatusCode.InternalServerError)
        }
        val retries = 2U
        val httpClient = HttpClient(mockEngine)
        val modelClient = ModelClientV2.builder()
            .client(httpClient)
            .retries(retries)
            .url(url)
            .build()

        assertFailsWith<ServerResponseException> {
            modelClient.init()
        }

        // We receive one initial request and then the configured number of retries.
        assertEquals(1 + retries.toInt(), mockEngine.requestHistory.size)
    }

    @Test
    fun retriesCanBeDisabled() = runTest {
        val url = "http://localhost/v2"
        val mockEngine = MockEngine {
            respondError(HttpStatusCode.InternalServerError)
        }
        val httpClient = HttpClient(mockEngine)
        val modelClient = ModelClientV2.builder()
            .client(httpClient)
            .retries(0U)
            .url(url)
            .build()

        assertFailsWith<ServerResponseException> {
            modelClient.init()
        }

        assertEquals(1, mockEngine.requestHistory.size)
    }
}
