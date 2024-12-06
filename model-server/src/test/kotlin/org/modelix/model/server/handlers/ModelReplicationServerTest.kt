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

import com.google.api.client.http.HttpStatusCodes
import io.kotest.assertions.ktor.client.shouldHaveContentType
import io.kotest.assertions.ktor.client.shouldHaveStatus
import io.kotest.matchers.shouldBe
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.defaultRequest
import io.ktor.client.request.accept
import io.ktor.client.request.delete
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.put
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.appendPathSegments
import io.ktor.http.contentType
import io.ktor.http.takeFrom
import io.ktor.http.withCharset
import io.ktor.serialization.kotlinx.KotlinxSerializationConverter
import io.ktor.serialization.kotlinx.json.DefaultJson
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.Application
import io.ktor.server.application.port
import io.ktor.server.engine.EmbeddedServer
import io.ktor.server.netty.NettyApplicationEngine
import io.ktor.server.testing.ApplicationTestBuilder
import io.ktor.server.testing.testApplication
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.onEmpty
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withTimeout
import org.modelix.model.api.IConceptReference
import org.modelix.model.client2.ModelClientV2
import org.modelix.model.client2.readVersionDelta
import org.modelix.model.client2.runWrite
import org.modelix.model.client2.useVersionStreamFormat
import org.modelix.model.lazy.RepositoryId
import org.modelix.model.server.api.v2.VersionDeltaStream
import org.modelix.model.server.api.v2.VersionDeltaStreamV2
import org.modelix.model.server.installDefaultServerPlugins
import org.modelix.model.server.runWithNettyServer
import org.modelix.model.server.store.InMemoryStoreClient
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.fail
import kotlin.time.Duration.Companion.seconds

class ModelReplicationServerTest {

    private data class Fixture(
        val storeClient: InMemoryStoreClient,
        val repositoriesManager: IRepositoriesManager,
        val modelReplicationServer: ModelReplicationServer,
    )

    private fun getDefaultModelReplicationServerFixture(): Fixture {
        val storeClient = InMemoryStoreClient()
        val repositoriesManager = RepositoriesManager(storeClient)
        return Fixture(
            storeClient,
            repositoriesManager,
            ModelReplicationServer(repositoriesManager),
        )
    }

    private fun runWithTestModelServer(
        fixture: Fixture = getDefaultModelReplicationServerFixture(),
        block: suspend ApplicationTestBuilder.(scope: CoroutineScope, fixture: Fixture) -> Unit,
    ) = testApplication {
        application {
            installDefaultServerPlugins(unitTestMode = true)
            fixture.modelReplicationServer.init(this)
            IdsApiImpl(fixture.repositoriesManager).init(this)
        }

        coroutineScope {
            block(this, fixture)
        }
    }

    @Test
    fun `pulling delta does not return objects twice`() = runWithTestModelServer { _, _ ->
        // Arrange
        val modelClient = ModelClientV2.builder().url("v2").client(client).build().also { it.init() }
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
                appendPathSegments("v2", "repositories", repositoryId.id, "branches", branchId.branchName)
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
    fun `responds with 404 when deleting a branch from a non-existent repository`() {
        runWithTestModelServer { _, _ ->
            val response = client.delete {
                url {
                    appendPathSegments("v2", "repositories", "doesnotexist", "branches", "does_not_exist")
                }
            }

            assertEquals(HttpStatusCode.NotFound, response.status)
            assertContains(response.bodyAsText(), "does not exist in repository")
        }
    }

    @Test
    fun `responds with 404 when deleting a non-existent branch`() {
        val repositoryId = RepositoryId("repo1")

        runWithTestModelServer { _, fixture ->
            fixture.repositoriesManager.createRepository(repositoryId, null)

            val response = client.delete {
                url {
                    appendPathSegments("v2", "repositories", repositoryId.id, "branches", "does_not_exist")
                }
            }

            assertEquals(HttpStatusCode.NotFound, response.status)
            assertContains(response.bodyAsText(), "does not exist in repository")
        }
    }

    @Test
    fun `responds with 400 when deleting from an invalid repository ID`() {
        runWithTestModelServer { _, _ ->
            val client = createClient { install(ContentNegotiation) { json() } }

            val response = client.delete {
                url {
                    appendPathSegments("v2", "repositories", "invalid with spaces", "branches", "master")
                }
            }
            val problem = response.body<Problem>()

            response shouldHaveStatus HttpStatusCode.BadRequest
            problem.type shouldBe "/problems/invalid-repository-id"
        }
    }

    @Test
    fun `responds with 404 when querying non-existent branch`() = runWithTestModelServer { _, _ ->
        val repositoryId = "abc"
        val branch = "non-existing-branch"
        val response = client.post("/v2/repositories/$repositoryId/branches/$branch/query")
        response shouldHaveStatus 404
        assertContains(response.bodyAsText(), "Branch '$branch' does not exist in repository '$repositoryId'")
    }

    @Test
    fun `successfully deletes existing branches`() {
        val repositoryId = RepositoryId("repo1")
        val branch = "testbranch"
        val defaultBranchRef = repositoryId.getBranchReference("master")

        runWithTestModelServer { _, fixture ->
            fixture.repositoriesManager.createRepository(repositoryId, null)
            fixture.repositoriesManager.mergeChanges(
                repositoryId.getBranchReference(branch),
                checkNotNull(fixture.repositoriesManager.getVersionHash(defaultBranchRef)) { "Default branch must exist" },
            )

            val response = client.delete {
                url {
                    appendPathSegments("v2", "repositories", repositoryId.id, "branches", branch)
                }
            }

            assertEquals(HttpStatusCode.NoContent, response.status)
            assertFalse(fixture.repositoriesManager.getBranchNames(repositoryId).contains(branch))
        }
    }

    @Test
    fun `server responds with error when failing to compute delta before starting to respond`() {
        // Arrange
        val storeClient = InMemoryStoreClient()
        val repositoriesManager = RepositoriesManager(storeClient)
        val faultyRepositoriesManager = object :
            IRepositoriesManager by repositoriesManager {
            override suspend fun computeDelta(repository: RepositoryId?, versionHash: String, baseVersionHash: String?): ObjectData {
                error("Unexpected error.")
            }
        }
        val modelReplicationServer = ModelReplicationServer(faultyRepositoriesManager)
        val repositoryId = RepositoryId("repo1")
        val branchRef = repositoryId.getBranchReference()

        runWithTestModelServer(
            Fixture(
                storeClient,
                faultyRepositoriesManager,
                modelReplicationServer,
            ),
        ) { _, _ ->
            repositoriesManager.createRepository(repositoryId, null)

            // Act
            val response = client.get {
                url {
                    appendPathSegments("v2", "repositories", repositoryId.id, "branches", branchRef.branchName)
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
        val repositoriesManager = RepositoriesManager(storeClient)
        val faultyRepositoriesManager = object :
            IRepositoriesManager by repositoriesManager {
            override suspend fun computeDelta(repository: RepositoryId?, versionHash: String, baseVersionHash: String?): ObjectData {
                val originalFlow = repositoriesManager.computeDelta(repository, versionHash, baseVersionHash).asFlow()
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

        fun createClient(server: EmbeddedServer<NettyApplicationEngine, NettyApplicationEngine.Configuration>): HttpClient {
            val port = server.environment.config.port
            return HttpClient(CIO) {
                defaultRequest {
                    url("http://localhost:$port")
                }
                install(HttpTimeout) {
                    requestTimeoutMillis = 5_000
                }
            }
        }

        val modelReplicationServer = ModelReplicationServer(faultyRepositoriesManager)
        val setupBlock = { application: Application -> modelReplicationServer.init(application) }
        val testBlock: suspend (server: EmbeddedServer<NettyApplicationEngine, NettyApplicationEngine.Configuration>) -> Unit = { server ->
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
    fun `client can pull versions in legacy version delta format`() = runWithTestModelServer { _, _ ->
        // Arrange
        val modelClient = ModelClientV2.builder().url("v2").client(client).build().also { it.init() }
        val repositoryId = RepositoryId("repo1")
        val branchId = repositoryId.getBranchReference("my-branch")
        modelClient.runWrite(branchId) { root ->
            root.addNewChild("aChild", -1, null as IConceptReference?)
        }

        // Act
        val response = client.get {
            headers[HttpHeaders.Accept] = VersionDeltaStream.CONTENT_TYPE.toString()
            url {
                appendPathSegments("v2", "repositories", repositoryId.id, "branches", branchId.branchName)
            }
        }
        val versionDelta = response.readVersionDelta()

        // Assert
        assertEquals(response.contentType(), VersionDeltaStream.CONTENT_TYPE)
        versionDelta.getObjectsAsFlow().assertNotEmpty()
    }

    @Test
    fun `responds with application problem+json in case of errors`() {
        val storeClient = InMemoryStoreClient()
        val repositoriesManager = RepositoriesManager(storeClient)
        val errorMessage = "expected test failure"
        val faultyRepositoriesManager = object :
            IRepositoriesManager by repositoriesManager {
            override fun getRepositories(): Set<RepositoryId> {
                error(errorMessage)
            }
        }
        val modelReplicationServer = ModelReplicationServer(faultyRepositoriesManager)

        runWithTestModelServer(
            Fixture(
                storeClient,
                faultyRepositoriesManager,
                modelReplicationServer,
            ),
        ) { _, _ ->
            val client = createClient {
                install(ContentNegotiation) { json() }
            }
            val response = client.get {
                url {
                    appendPathSegments("v2", "repositories")
                }
            }

            assertEquals(HttpStatusCode.InternalServerError, response.status)
            assertEquals(ContentType.parse("application/problem+json"), response.contentType())
            assertEquals(
                Problem(
                    title = "Internal server error",
                    detail = errorMessage,
                    status = HttpStatusCode.InternalServerError.value,
                    type = "/problems/unclassified-internal-server-error",
                ),
                response.body<Problem>(),
            )
        }
    }

    @Test
    fun `getRepositoryBranch responds with version delta v2 if requested`() {
        val repositoryId = RepositoryId("repo1")

        runWithTestModelServer { _, fixture ->
            fixture.repositoriesManager.createRepository(repositoryId, null)

            val response = client.get {
                url {
                    appendPathSegments("v2", "repositories", repositoryId.id, "branches", "master")
                }
                accept(VersionDeltaStreamV2.CONTENT_TYPE)
            }

            response shouldHaveStatus HttpStatusCodes.STATUS_CODE_OK
            response.shouldHaveContentType(VersionDeltaStreamV2.CONTENT_TYPE)
        }
    }

    @Test
    fun `getRepositoryBranch responds with v1 branch if requested`() {
        val repositoryId = RepositoryId("repo1")
        val branchV1ContentType =
            ContentType.parse("application/x.modelix.branch+json;version=1").withCharset(Charsets.UTF_8)

        runWithTestModelServer { _, fixture ->
            fixture.repositoriesManager.createRepository(repositoryId, null)
            val client = createClient {
                install(ContentNegotiation) {
                    json()
                    register(branchV1ContentType, KotlinxSerializationConverter(DefaultJson))
                }
            }

            val response = client.get {
                url {
                    appendPathSegments("v2", "repositories", repositoryId.id, "branches", "master")
                }
                accept(branchV1ContentType)
            }

            response.shouldHaveContentType(branchV1ContentType)
            response.body<BranchV1>() shouldBe BranchV1(
                "master",
                fixture.repositoriesManager.pollVersionHash(repositoryId.getBranchReference("master"), null),
            )
        }
    }

    @Test
    fun `putRepositoryObjects detects missing values`() = runWithTestModelServer { _, _ ->
        val client = createClient { install(ContentNegotiation) { json() } }
        val modelClient = ModelClientV2.builder().url("v2").client(client).build().also { it.init() }
        val repositoryId = RepositoryId("repo1")
        modelClient.initRepository(repositoryId)
        val invalidRequestBody = "7-m7S*flQz_CNc9F3ipSQ5Iz7SZJSyn2r0_1PPVvh8yA"

        val response = client.put {
            url {
                takeFrom("v2")
                appendPathSegments("repositories", repositoryId.id, "objects")
            }
            contentType(ContentType.Text.Plain)
            setBody(invalidRequestBody)
        }
        val problem = response.body<Problem>()

        response shouldHaveStatus HttpStatusCode.BadRequest
        problem.type shouldBe "/problems/object-key-without-object-value"
    }

    @Test
    fun `putRepositoryObjects reports invalid key`() = runWithTestModelServer { _, _ ->
        val client = createClient { install(ContentNegotiation) { json() } }
        val modelClient = ModelClientV2.builder().url("v2").client(client).build().also { it.init() }
        val repositoryId = RepositoryId("repo1")
        modelClient.initRepository(repositoryId)
        val invalidRequestBody = "not-a-valid-key"

        val response = client.put {
            url {
                takeFrom("v2")
                appendPathSegments("repositories", repositoryId.id, "objects")
            }
            contentType(ContentType.Text.Plain)
            setBody(invalidRequestBody)
        }
        val problem = response.body<Problem>()

        response shouldHaveStatus HttpStatusCode.BadRequest
        problem.type shouldBe "/problems/invalid-object-key"
    }

    @Test
    fun `putRepositoryObjects detects mismatch between object key and object value`() = runWithTestModelServer { _, _ ->
        val client = createClient { install(ContentNegotiation) { json() } }
        val modelClient = ModelClientV2.builder().url("v2").client(client).build().also { it.init() }
        val repositoryId = RepositoryId("repo1")
        modelClient.initRepository(repositoryId)
        val invalidRequestBody = "7-m7S*flQz_CNc9F3ipSQ5Iz7SZJSyn2r0_1PPVvh8yA\ninvalidObjectValue"

        val response = client.put {
            url {
                takeFrom("v2")
                appendPathSegments("repositories", repositoryId.id, "objects")
            }
            contentType(ContentType.Text.Plain)
            setBody(invalidRequestBody)
        }
        val problem = response.body<Problem>()

        response shouldHaveStatus HttpStatusCode.BadRequest
        problem.type shouldBe "/problems/mismatching-object-and-value"
    }

    @Test
    fun `postRepositoryObjectsGetAll fails for missing object value value`() = runWithTestModelServer { _, _ ->
        val client = createClient { install(ContentNegotiation) { json() } }
        val modelClient = ModelClientV2.builder().url("v2").client(client).build().also { it.init() }
        val repositoryId = RepositoryId("repo1")
        modelClient.initRepository(repositoryId)
        val invalidRequestBody = "7-m7S*flQz_CNc9F3ipSQ5Iz7SZJSyn2r0_1PPVvh8yA"

        val response = client.post {
            url {
                takeFrom("v2")
                appendPathSegments("repositories", repositoryId.id, "objects", "getAll")
            }
            contentType(ContentType.Text.Plain)
            setBody(invalidRequestBody)
        }
        val problem = response.body<Problem>()

        response shouldHaveStatus HttpStatusCode.NotFound
        problem.type shouldBe "/problems/object-value-not-found"
    }
}

fun <T> Flow<T>.assertNotEmpty(additionalMessage: () -> String = { "" }): Flow<T> {
    return onEmpty { throw IllegalArgumentException("At least one element was expected. " + additionalMessage()) }
}
