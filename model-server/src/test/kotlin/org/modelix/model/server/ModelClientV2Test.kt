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

package org.modelix.model.server

import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.install
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.resources.Resources
import io.ktor.server.routing.IgnoreTrailingSlash
import io.ktor.server.testing.ApplicationTestBuilder
import io.ktor.server.testing.testApplication
import io.ktor.server.websocket.WebSockets
import org.modelix.authorization.installAuthentication
import org.modelix.model.api.IConceptReference
import org.modelix.model.api.ITree
import org.modelix.model.api.PBranch
import org.modelix.model.client2.ModelClientV2
import org.modelix.model.client2.runWrite
import org.modelix.model.client2.runWriteOnBranch
import org.modelix.model.lazy.CLTree
import org.modelix.model.lazy.CLVersion
import org.modelix.model.lazy.RepositoryId
import org.modelix.model.operations.OTBranch
import org.modelix.model.persistent.IKVValue
import org.modelix.model.server.handlers.ModelReplicationServer
import org.modelix.model.server.store.InMemoryStoreClient
import org.modelix.modelql.core.count
import org.modelix.modelql.untyped.allChildren
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ModelClientV2Test {

    private fun runTest(block: suspend ApplicationTestBuilder.() -> Unit) = testApplication {
        application {
            installAuthentication(unitTestMode = true)
            install(ContentNegotiation) {
                json()
            }
            install(WebSockets)
            install(Resources)
            install(IgnoreTrailingSlash)
            ModelReplicationServer(InMemoryStoreClient()).init(this)
        }
        block()
    }

    private suspend fun ApplicationTestBuilder.createModelClient(): ModelClientV2 {
        val url = "http://localhost/v2"
        val modelClient = ModelClientV2.builder().url(url).client(client).build().also { it.init() }
        return modelClient
    }

    @Test
    fun test_t1() = runTest {
        val client = createModelClient()

        val repositoryId = RepositoryId("repo1")
        val initialVersion = client.initRepository(repositoryId)
        assertEquals(0, initialVersion.getTree().getAllChildren(ITree.ROOT_ID).count())

        val branch = OTBranch(PBranch(initialVersion.getTree(), client.getIdGenerator()), client.getIdGenerator(), client.store)
        branch.runWriteT { t ->
            t.addNewChild(ITree.ROOT_ID, "role", -1, null as IConceptReference?)
        }
        val (ops, newTree) = branch.operationsAndTree
        val newVersion = CLVersion.createRegularVersion(
            client.getIdGenerator().generate(),
            null,
            null,
            newTree as CLTree,
            initialVersion as CLVersion,
            ops.map { it.getOriginalOp() }.toTypedArray(),
        )

        assertEquals(
            client.listBranches(repositoryId).toSet(),
            setOf(repositoryId.getBranchReference()),
        )

        val branchId = repositoryId.getBranchReference("my-branch")
        val mergedVersion = client.push(branchId, newVersion, initialVersion)
        assertEquals(1, mergedVersion.getTree().getAllChildren(ITree.ROOT_ID).count())

        assertEquals(
            client.listBranches(repositoryId).toSet(),
            setOf(repositoryId.getBranchReference(), branchId),
        )
    }

    @Test
    fun modelqlSmokeTest() = runTest {
        val client = createModelClient()
        val repositoryId = RepositoryId("repo1")
        val branchRef = repositoryId.getBranchReference()
        val initialVersion = client.initRepository(repositoryId)
        val size = client.query(branchRef) { it.allChildren().count() }
        assertEquals(0, size)

        val size2 = client.query(repositoryId, initialVersion.getContentHash()) { it.allChildren().count() }
        assertEquals(0, size2)
    }

    @Test
    fun testSlashesInPathSegmentsFromRepositoryIdAndBranchId() = runTest {
        val client = createModelClient()
        val repositoryId = RepositoryId("repo/v1")
        val initialVersion = client.initRepository(repositoryId)
        val branchId = repositoryId.getBranchReference("my-branch/v1")
        client.push(branchId, initialVersion, null)
        assertEquals(
            client.listBranches(repositoryId).toSet(),
            setOf(repositoryId.getBranchReference(), branchId),
        )
    }

    @Test
    fun `user id can be provided to client after creation`() = runTest {
        val modelClient = createModelClient()
        val userId = "a_user_id"
        modelClient.setClientProvideUserId(userId)

        assertEquals(userId, modelClient.getUserId())
    }

    @Test
    fun `user id provided by client can be removed`() = runTest {
        val url = "http://localhost/v2"
        val userId = "a_user_id"
        val modelClient = ModelClientV2
            .builder()
            .url(url)
            .client(client)
            .userId(userId)
            .build()
        modelClient.init()

        assertEquals(userId, modelClient.getUserId())
        modelClient.setClientProvideUserId(null)

        assertEquals("localhost", modelClient.getUserId())
    }

    @Test
    fun `newly created repository can be removed`() = runTest {
        val client = createModelClient()
        val repositoryId = RepositoryId(UUID.randomUUID().toString())
        client.initRepository(repositoryId)

        val success = client.deleteRepository(repositoryId)
        val containsRepository = client.listRepositories().contains(repositoryId)

        assertTrue(success)
        assertFalse(containsRepository)
    }

    @Test
    fun `non-existing repository cannot be removed`() = runTest {
        val client = createModelClient()
        val repositoryId = RepositoryId(UUID.randomUUID().toString())

        val success = client.deleteRepository(repositoryId)
        val containsRepository = client.listRepositories().contains(repositoryId)

        assertFalse(success)
        assertFalse(containsRepository)
    }

    @Test
    fun `pulling existing versions pulls all referenced objects`() = runTest {
        // Arrange
        val modelClientForArrange = createModelClient()
        val modelClientForAssert = createModelClient()
        val repositoryId = RepositoryId("repo1")
        val branchId = repositoryId.getBranchReference("my-branch")
        modelClientForArrange.runWrite(branchId) { root ->
            // Creating many children makes the flow emitting many values at once.
            repeat(100) {
                root.addNewChild("aChild", -1, null as IConceptReference?)
            }
        }

        // Act
        val versionPulled = modelClientForAssert.pullIfExists(branchId)!! as CLVersion

        // Assert
        fun checkAllReferencedEntriesExistInStore(referencingEntry: IKVValue) {
            for (entryReference in referencingEntry.getReferencedEntries()) {
                // Check that the store also provides each referenced KVEntry.
                // `getValue` would fail if this is not the case.
                val referencedEntry = entryReference.getValue(versionPulled.store)
                checkAllReferencedEntriesExistInStore(referencedEntry)
            }
        }
        checkAllReferencedEntriesExistInStore(versionPulled.data!!)
    }

    @Test
    fun `writing no data does not create empty versions`() = runTest {
        // Arrange
        val modelClient = createModelClient()
        val repositoryId = RepositoryId("repo1")
        val branchId = repositoryId.getBranchReference("master")
        modelClient.initRepository(repositoryId)
        val versionAfterBeforeWrite = modelClient.pullIfExists(branchId)!!

        // Act
        modelClient.runWriteOnBranch(branchId) {
            // do nothing
        }

        // Assert
        val versionAfterRunWrite = modelClient.pullIfExists(branchId)!!
        assertEquals(versionAfterBeforeWrite.getContentHash(), versionAfterRunWrite.getContentHash())
    }

    @Test
    fun `client can load version`() = runTest {
        val modelClient = createModelClient()
        val repositoryId = RepositoryId("aRepo")
        val initialVersion = modelClient.initRepository(repositoryId)

        val loadedVersion = modelClient.loadVersion(repositoryId, initialVersion.getContentHash(), initialVersion)

        assertEquals(initialVersion.getContentHash(), loadedVersion.getContentHash())
    }

    @Test
    fun `client can load version (deprecated endpoint without repository)`() = runTest {
        val modelClient = createModelClient()
        val repositoryId = RepositoryId("aRepo")
        val initialVersion = modelClient.initRepository(repositoryId)

        val loadedVersion = modelClient.loadVersion(initialVersion.getContentHash(), initialVersion)

        assertEquals(initialVersion.getContentHash(), loadedVersion.getContentHash())
    }
}
