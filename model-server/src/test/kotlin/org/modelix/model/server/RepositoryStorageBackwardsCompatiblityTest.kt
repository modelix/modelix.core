package org.modelix.model.server

import io.ktor.server.testing.ApplicationTestBuilder
import io.ktor.server.testing.testApplication
import org.modelix.model.async.BulkAsyncStore
import org.modelix.model.async.LegacyKeyValueStoreAsAsyncStore
import org.modelix.model.client.RestWebModelClient
import org.modelix.model.lazy.CLTree
import org.modelix.model.lazy.CLVersion
import org.modelix.model.lazy.RepositoryId
import org.modelix.model.server.handlers.IdsApiImpl
import org.modelix.model.server.handlers.KeyValueLikeModelServer
import org.modelix.model.server.handlers.ModelReplicationServer
import org.modelix.model.server.handlers.RepositoriesManager
import org.modelix.model.server.store.InMemoryStoreClient
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFails

class RepositoryStorageBackwardsCompatiblityTest {
    private fun runTest(block: suspend ApplicationTestBuilder.() -> Unit) = testApplication {
        application {
            installDefaultServerPlugins(unitTestMode = true)
            val store = InMemoryStoreClient()
            val repositoriesManager = RepositoriesManager(store)
            KeyValueLikeModelServer(repositoriesManager).init(this)
            ModelReplicationServer(repositoriesManager).init(this)
            IdsApiImpl(repositoriesManager).init(this)
        }
        block()
    }

    @Test
    fun `model client V1 can create a repository that is visible to the V2 client`() = runTest {
        createModelClient().use { clientv2 ->
            RestWebModelClient(baseUrl = "http://localhost/", providedClient = client).use { clientv1 ->
                val repositoryId = RepositoryId("repo1")
                val branchReference = repositoryId.getBranchReference()

                assertEquals(listOf(), clientv2.listRepositories())
                assertFails { clientv2.pullHash(branchReference) }

                val store = BulkAsyncStore(LegacyKeyValueStoreAsAsyncStore(clientv1))
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
        }
    }

    @Test
    fun `model client V2 can create a repository that is visible to the V1 client`() = runTest {
        createModelClient().use { clientv2 ->
            RestWebModelClient(baseUrl = "http://localhost/", providedClient = client).use { clientv1 ->
                val repositoryId = RepositoryId("repo1")
                val branchReference = repositoryId.getBranchReference()
                assertEquals(listOf(), clientv2.listRepositories())

                val initialVersion = clientv2.initRepository(repositoryId, legacyGlobalStorage = true)

                assertEquals(initialVersion.getContentHash(), clientv1.getA(branchReference.getKey()))
            }
        }
    }

    @Test
    fun `model client V2 can create a repository that is not visible to the V1 client`() = runTest {
        createModelClient().use { clientv2 ->
            RestWebModelClient(baseUrl = "http://localhost/", providedClient = client).use { clientv1 ->
                val repositoryId = RepositoryId("repo1")
                val branchReference = repositoryId.getBranchReference()
                assertEquals(listOf(), clientv2.listRepositories())

                clientv2.initRepository(repositoryId)

                assertEquals(null, clientv1.getA(branchReference.getKey()))
            }
        }
    }
}
