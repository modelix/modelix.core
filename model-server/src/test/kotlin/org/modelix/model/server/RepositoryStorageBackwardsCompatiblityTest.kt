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

package org.modelix.model.server

import io.ktor.server.routing.routing
import io.ktor.server.testing.ApplicationTestBuilder
import io.ktor.server.testing.testApplication
import org.modelix.authorization.installAuthentication
import org.modelix.model.InMemoryModels
import org.modelix.model.client.RestWebModelClient
import org.modelix.model.lazy.CLTree
import org.modelix.model.lazy.CLVersion
import org.modelix.model.lazy.NonCachingObjectStore
import org.modelix.model.lazy.RepositoryId
import org.modelix.model.server.handlers.IdsApiImpl
import org.modelix.model.server.handlers.KeyValueLikeModelServer
import org.modelix.model.server.handlers.ModelReplicationServer
import org.modelix.model.server.handlers.RepositoriesManager
import org.modelix.model.server.store.InMemoryStoreClient
import org.modelix.model.server.store.LocalModelClient
import org.modelix.model.server.store.forContextRepository
import org.modelix.model.server.store.forGlobalRepository
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFails

class RepositoryStorageBackwardsCompatiblityTest {
    private fun runTest(block: suspend ApplicationTestBuilder.() -> Unit) = testApplication {
        application {
            installDefaultServerPlugins()
            installAuthentication(unitTestMode = true)
            val store = InMemoryStoreClient()
            val repositoriesManager = RepositoriesManager(LocalModelClient(store.forContextRepository()))
            KeyValueLikeModelServer(repositoriesManager, store.forGlobalRepository(), InMemoryModels()).init(this)
            ModelReplicationServer(repositoriesManager).init(this)
            routing {
                IdsApiImpl(repositoriesManager).installRoutes(this)
            }
        }
        block()
    }

    @Test
    fun `model client V1 can create a repository that is visible to the V2 client`() = runTest {
        val clientv2 = createModelClient()
        val clientv1 = RestWebModelClient(baseUrl = "http://localhost/", providedClient = client)
        val repositoryId = RepositoryId("repo1")
        val branchReference = repositoryId.getBranchReference()

        assertEquals(listOf(), clientv2.listRepositories())
        assertFails { clientv2.pullHash(branchReference) }

        val store = NonCachingObjectStore(clientv1)
        val idGenerator = clientv1.idGenerator
        val initialVersion = CLVersion.createRegularVersion(
            id = idGenerator.generate(),
            author = "unit-test",
            tree = CLTree.builder(store).repositoryId(repositoryId).build(),
            baseVersion = null,
            operations = emptyArray(),
        )
        initialVersion.write()
        clientv1.putA(branchReference.getKey(), initialVersion.getContentHash())

        assertEquals(listOf(repositoryId), clientv2.listRepositories())
        assertEquals(initialVersion.getContentHash(), clientv2.pullHash(branchReference))
    }

    @Test
    fun `model client V2 can create a repository that is visible to the V1 client`() = runTest {
        val clientv2 = createModelClient()
        val clientv1 = RestWebModelClient(baseUrl = "http://localhost/", providedClient = client)

        val repositoryId = RepositoryId("repo1")
        val branchReference = repositoryId.getBranchReference()
        assertEquals(listOf(), clientv2.listRepositories())

        val initialVersion = clientv2.initRepository(repositoryId, legacyGlobalStorage = true)

        assertEquals(initialVersion.getContentHash(), clientv1.getA(branchReference.getKey()))
    }

    @Test
    fun `model client V2 can create a repository that is not visible to the V1 client`() = runTest {
        val clientv2 = createModelClient()
        val clientv1 = RestWebModelClient(baseUrl = "http://localhost/", providedClient = client)

        val repositoryId = RepositoryId("repo1")
        val branchReference = repositoryId.getBranchReference()
        assertEquals(listOf(), clientv2.listRepositories())

        clientv2.initRepository(repositoryId)

        assertEquals(null, clientv1.getA(branchReference.getKey()))
    }
}
