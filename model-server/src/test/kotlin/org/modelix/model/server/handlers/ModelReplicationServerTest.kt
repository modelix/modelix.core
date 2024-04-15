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

package org.modelix.model.server.handlers

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.defaultRequest
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.http.HttpStatusCode
import io.ktor.http.appendPathSegments
import io.ktor.http.takeFrom
import io.ktor.server.application.Application
import io.ktor.server.netty.NettyApplicationEngine
import io.ktor.server.testing.ApplicationTestBuilder
import io.ktor.server.testing.testApplication
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withTimeout
import org.modelix.authorization.installAuthentication
import org.modelix.model.InMemoryModels
import org.modelix.model.api.IConceptReference
import org.modelix.model.client2.ModelClientV2
import org.modelix.model.client2.readVersionDelta
import org.modelix.model.client2.runWrite
import org.modelix.model.client2.useVersionStreamFormat
import org.modelix.model.lazy.RepositoryId
import org.modelix.model.server.installDefaultServerPlugins
import org.modelix.model.server.runWithNettyServer
import org.modelix.model.server.store.InMemoryStoreClient
import org.modelix.model.server.store.LocalModelClient
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.fail
import kotlin.time.Duration.Companion.seconds

class ModelReplicationServerTest {

    private fun getDefaultModelReplicationServer(): ModelReplicationServer {
        val storeClient = InMemoryStoreClient()
        val modelClient = LocalModelClient(storeClient)
        val repositoriesManager = RepositoriesManager(modelClient)
        return ModelReplicationServer(repositoriesManager, modelClient, InMemoryModels())
    }

    private fun runWithTestModelServer(
        modelReplicationServer: ModelReplicationServer = getDefaultModelReplicationServer(),
        block: suspend ApplicationTestBuilder.(scope: CoroutineScope) -> Unit,
    ) = testApplication {
        application {
            installAuthentication(unitTestMode = true)
            installDefaultServerPlugins()
            modelReplicationServer.init(this)
        }

        coroutineScope {
            block(this)
        }
    }

    @Test
    fun `pulling delta does not return objects twice`() = runWithTestModelServer {
        // Arrange
        val url = "http://localhost/v2"
        val modelClient = ModelClientV2.builder().url(url).client(client).build().also { it.init() }
        val repositoryId = RepositoryId("repo1")
        val branchId = repositoryId.getBranchReference("my-branch")
        // By calling modelClient.runWrite twice, we create to versions.
        // Those two versions will share objects.
        modelClient.runWrite(branchId) { root ->
            root.addNewChild("aChild", -1, null as IConceptReference?)
        }
        modelClient.runWrite(branchId) { root ->
            root.addNewChild("aChild", -1, null as IConceptReference?)
        }

        // Act
        val response = client.get {
            url {
                takeFrom(url)
                appendPathSegments("repositories", repositoryId.id, "branches", branchId.branchName)
            }
            useVersionStreamFormat()
        }
        val versionDelta = response.readVersionDelta()

        // Assert
        val seenHashes = mutableSetOf<String>()
        versionDelta.getObjectsAsFlow().collect { (hash, _) ->
            val wasSeenBefore = !seenHashes.add(hash)
            if (wasSeenBefore) {
                // We should not send the same object (with the same hash more than once)
                // even if we got with versions that share data.
                fail("Hash $hash sent more than once.")
            }
        }
    }

    @Test
    fun `server responds with error when failing to compute delta before starting to respond`() {
        // Arrange
        val storeClient = InMemoryStoreClient()
        val modelClient = LocalModelClient(storeClient)
        val repositoriesManager = RepositoriesManager(modelClient)
        val faultyRepositoriesManager = object :
            IRepositoriesManager by repositoriesManager {
            override suspend fun computeDelta(versionHash: String, baseVersionHash: String?): ObjectData {
                error("Unexpected error.")
            }
        }
        val modelReplicationServer = ModelReplicationServer(faultyRepositoriesManager, modelClient, InMemoryModels())
        val url = "http://localhost/v2"
        val repositoryId = RepositoryId("repo1")
        val branchRef = repositoryId.getBranchReference()

        runWithTestModelServer(modelReplicationServer) {
            repositoriesManager.createRepository(repositoryId, null)

            // Act
            val response = client.get {
                url {
                    takeFrom(url)
                    appendPathSegments("repositories", repositoryId.id, "branches", branchRef.branchName)
                }
                useVersionStreamFormat()
            }

            // Assert
            assertEquals(HttpStatusCode.InternalServerError, response.status)
        }
    }

    @Test
    fun `server closes connection when failing to compute delta after starting to respond`() = runTest {
        // Arrange
        val repositoryId = RepositoryId("repo1")
        val branchRef = repositoryId.getBranchReference()
        val storeClient = InMemoryStoreClient()
        val modelClient = LocalModelClient(storeClient)
        val repositoriesManager = RepositoriesManager(modelClient)
        val faultyRepositoriesManager = object :
            IRepositoriesManager by repositoriesManager {
            override suspend fun computeDelta(versionHash: String, baseVersionHash: String?): ObjectData {
                val originalFlow = repositoriesManager.computeDelta(versionHash, baseVersionHash).asFlow()
                val brokenFlow = channelFlow<Pair<String, String>> {
                    error("Unexpected error.")
                }
                return ObjectDataFlow(
                    flow {
                        emitAll(originalFlow)
                        emitAll(brokenFlow)
                    },
                )
            }
        }
        repositoriesManager.createRepository(repositoryId, null)

        suspend fun createClient(server: NettyApplicationEngine): HttpClient {
            val port = server.resolvedConnectors().first().port
            return HttpClient(CIO) {
                defaultRequest {
                    url("http://localhost:$port")
                }
                install(HttpTimeout) {
                    requestTimeoutMillis = 5_000
                }
            }
        }

        val modelReplicationServer = ModelReplicationServer(faultyRepositoriesManager, modelClient, InMemoryModels())
        val setupBlock = { application: Application -> modelReplicationServer.init(application) }
        val testBlock: suspend (server: NettyApplicationEngine) -> Unit = { server ->
            withTimeout(10.seconds) {
                val client = createClient(server)
                // Act
                val response = client.get {
                    url {
                        takeFrom(url)
                        appendPathSegments("v2", "repositories", repositoryId.id, "branches", branchRef.branchName)
                    }
                    useVersionStreamFormat()
                }

                // Assert
                // The response should be delivered even if an exception is thrown inside the flow.
                assertEquals(HttpStatusCode.OK, response.status)
            }
        }
        runWithNettyServer(setupBlock, testBlock)
    }

    @Test
    fun `respond 400 Bad for invalid query parameter useRoleId`() = runWithTestModelServer {
        val url = "http://localhost/v2"
        val repositoryId = RepositoryId("repo1")

        // Act
        val response = client.post {
            url {
                takeFrom(url)
                appendPathSegments("repositories", repositoryId.id, "init")
                parameters["useRoleIds"] = "False"
            }
        }

        // Assert
        assertEquals(HttpStatusCode.BadRequest, response.status)
        assertEquals("Value `False` for parameter `useRoleIds` is not a valid boolean. Valid booleans are `true` and `false`.", response.body())
    }
}
