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
import io.ktor.server.testing.ApplicationTestBuilder
import io.ktor.server.testing.testApplication
import io.ktor.server.websocket.WebSockets
import org.modelix.authorization.installAuthentication
import org.modelix.model.api.IConceptReference
import org.modelix.model.api.ITree
import org.modelix.model.api.PBranch
import org.modelix.model.client2.ModelClientV2
import org.modelix.model.lazy.CLTree
import org.modelix.model.lazy.CLVersion
import org.modelix.model.lazy.RepositoryId
import org.modelix.model.operations.OTBranch
import org.modelix.model.server.handlers.ModelReplicationServer
import org.modelix.model.server.store.InMemoryStoreClient
import org.modelix.modelql.core.count
import org.modelix.modelql.untyped.allChildren
import kotlin.test.Test
import kotlin.test.assertEquals

class ModelClientV2Test {

    private fun runTest(block: suspend ApplicationTestBuilder.() -> Unit) = testApplication {
        application {
            installAuthentication(unitTestMode = true)
            install(ContentNegotiation) {
                json()
            }
            install(WebSockets)
            ModelReplicationServer(InMemoryStoreClient()).init(this)
        }
        block()
    }

    @Test
    fun test_t1() = runTest {
        val url = "http://localhost/v2"
        val client = ModelClientV2.builder().url(url).client(client).build().also { it.init() }

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
        val url = "http://localhost/v2"
        val client = ModelClientV2.builder().url(url).client(client).build().also { it.init() }

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
        val url = "http://localhost/v2"
        val client = ModelClientV2.builder().url(url).client(client).build()
        client.init()
        val repositoryId = RepositoryId("repo/v1")
        val initialVersion = client.initRepository(repositoryId)
        val branchId = repositoryId.getBranchReference("my-branch/v1")
        client.push(branchId, initialVersion, null)
        assertEquals(
            client.listBranches(repositoryId).toSet(),
            setOf(repositoryId.getBranchReference(), branchId),
        )
    }
}
