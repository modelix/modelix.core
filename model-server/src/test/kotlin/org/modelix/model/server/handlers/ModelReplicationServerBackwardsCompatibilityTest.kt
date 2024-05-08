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

import io.ktor.server.testing.ApplicationTestBuilder
import io.ktor.server.testing.testApplication
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.coroutineScope
import org.modelix.authorization.installAuthentication
import org.modelix.model.InMemoryModels
import org.modelix.model.client.RestWebModelClient
import org.modelix.model.client2.ModelClientV2
import org.modelix.model.lazy.RepositoryId
import org.modelix.model.server.installDefaultServerPlugins
import org.modelix.model.server.store.InMemoryStoreClient
import org.modelix.model.server.store.LocalModelClient
import org.modelix.model.server.store.forGlobalRepository
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class ModelReplicationServerBackwardsCompatibilityTest {

    private fun runWithTestModelServer(
        block: suspend ApplicationTestBuilder.(scope: CoroutineScope) -> Unit,
    ) = testApplication {
        val storeClient = InMemoryStoreClient()
        val modelClient = LocalModelClient(storeClient)
        val repositoriesManager = RepositoriesManager(modelClient)
        val modelReplicationServer = ModelReplicationServer(repositoriesManager, modelClient, InMemoryModels())
        val keyValueLikeModelServer = KeyValueLikeModelServer(repositoriesManager, storeClient.forGlobalRepository(), InMemoryModels())
        application {
            installAuthentication(unitTestMode = true)
            installDefaultServerPlugins()
            modelReplicationServer.init(this)
            keyValueLikeModelServer.init(this)
        }

        coroutineScope {
            block(this)
        }
    }

    @Test
    fun `master branch of repository initialized by V2 API is visible through V1 API`() {
        val urlV1 = "http://localhost"
        val urlV2 = "http://localhost/v2"
        val repositoryId = RepositoryId("repo1")
        val defaultBranchRef = repositoryId.getBranchReference("master")

        runWithTestModelServer {
            val modelClientV2 = ModelClientV2
                .builder()
                .url(urlV2)
                .client(client)
                .build()
            modelClientV2.init()
            val modelClientV1 = RestWebModelClient(baseUrl = urlV1, providedClient = client)

            val initialVersion = modelClientV2.initRepository(repositoryId, legacyGlobalStorage = true)

            val branchVersionVisibleInV1 = modelClientV1.getA(defaultBranchRef.getKey())
            assertEquals(initialVersion.getContentHash(), branchVersionVisibleInV1)
        }
    }

    @Test
    fun `branch is deleted through the V2 API is deleted in the V1 API`() {
        val urlV1 = "http://localhost"
        val urlV2 = "http://localhost/v2"
        val repositoryId = RepositoryId("repo1")
        val branchRef = repositoryId.getBranchReference("master")

        runWithTestModelServer {
            val modelClientV2 = ModelClientV2
                .builder()
                .url(urlV2)
                .client(client)
                .build()
            modelClientV2.init()
            val modelClientV1 = RestWebModelClient(baseUrl = urlV1, providedClient = client)
            val initialVersion = modelClientV2.initRepository(repositoryId, legacyGlobalStorage = true)
            val branchVersionVisibleInV1BeforeDelete = modelClientV1.getA(branchRef.getKey())
            assertEquals(
                initialVersion.getContentHash(),
                branchVersionVisibleInV1BeforeDelete,
                "Test setup should create branch in a way that make it visible in the V1 API.",
            )

            modelClientV2.deleteBranch(branchRef)

            val branchVersionVisibleInV1AfterDelete = modelClientV1.getA(branchRef.getKey())
            assertNull(branchVersionVisibleInV1AfterDelete)
        }
    }
}
