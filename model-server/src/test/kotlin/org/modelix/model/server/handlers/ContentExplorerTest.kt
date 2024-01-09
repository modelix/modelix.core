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

import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.resources.Resources
import io.ktor.server.routing.IgnoreTrailingSlash
import io.ktor.server.testing.ApplicationTestBuilder
import io.ktor.server.testing.testApplication
import io.ktor.server.websocket.WebSockets
import org.modelix.model.client.successful
import org.modelix.model.lazy.CLVersion
import org.modelix.model.server.api.v2.VersionDelta
import org.modelix.model.server.store.InMemoryStoreClient
import org.modelix.model.server.store.LocalModelClient
import kotlin.test.Test
import kotlin.test.assertTrue
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation as ClientContentNegotiation

class ContentExplorerTest {

    private val repoId = "test-repo"
    private val modelClient = LocalModelClient(InMemoryStoreClient())
    private val repoManager = RepositoriesManager(modelClient)

    private fun runTest(body: suspend (ApplicationTestBuilder.() -> Unit)) {
        testApplication {
            install(WebSockets)
            install(ContentNegotiation) { json() }
            install(Resources)
            install(IgnoreTrailingSlash)
            application {
                ModelReplicationServer(repoManager).init(this)
                ContentExplorer(modelClient, repoManager).init(this)
            }

            body()
        }
    }

    @Test
    fun `node inspector finds root node`() = runTest {
        val client = createClient {
            install(ClientContentNegotiation) { json() }
        }

        val delta: VersionDelta = client.post("/v2/repositories/$repoId/init").body()

        val versionHash = delta.versionHash
        val version = CLVersion.loadFromHash(versionHash, modelClient.storeCache)
        val nodeId = checkNotNull(version.getTree().root?.id)

        val response = client.get("/content/$versionHash/$nodeId/")
        assertTrue(response.successful)
    }
}
