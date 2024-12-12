package org.modelix.model.server.handlers

import io.ktor.server.testing.ApplicationTestBuilder
import io.ktor.server.testing.testApplication
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.coroutineScope
import org.modelix.model.client.RestWebModelClient
import org.modelix.model.client2.ModelClientV2
import org.modelix.model.lazy.RepositoryId
import org.modelix.model.server.installDefaultServerPlugins
import org.modelix.model.server.store.InMemoryStoreClient
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class ModelReplicationServerBackwardsCompatibilityTest {

    private fun runWithTestModelServer(
        block: suspend ApplicationTestBuilder.(scope: CoroutineScope) -> Unit,
    ) = testApplication {
        val storeClient = InMemoryStoreClient()
        val repositoriesManager = RepositoriesManager(storeClient)
        val modelReplicationServer = ModelReplicationServer(repositoriesManager)
        val keyValueLikeModelServer = KeyValueLikeModelServer(repositoriesManager)
        application {
            installDefaultServerPlugins()
            modelReplicationServer.init(this)
            keyValueLikeModelServer.init(this)
            IdsApiImpl(repositoriesManager).init(this)
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
