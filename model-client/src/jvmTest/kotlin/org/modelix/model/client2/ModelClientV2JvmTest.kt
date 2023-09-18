/*
 * Copyright (c) 2023.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.modelix.model.client2

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respondError
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
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
        assertFailsWith<CancellationException>("Parent job is Completed") {
            modelClient.init()
        }
        // `Closable` implies, that `.close` method is idempotent.
        modelClient.close()
        assertFailsWith<CancellationException>("Parent job is Completed") {
            modelClient.init()
        }
    }
}
