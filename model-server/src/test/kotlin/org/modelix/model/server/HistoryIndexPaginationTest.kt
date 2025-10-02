package org.modelix.model.server

import io.ktor.server.testing.ApplicationTestBuilder
import io.ktor.server.testing.testApplication
import mu.KotlinLogging
import org.modelix.model.IVersion
import org.modelix.model.client2.IModelClientV2
import org.modelix.model.historyAsSequence
import org.modelix.model.lazy.RepositoryId
import org.modelix.model.server.api.RepositoryConfig
import org.modelix.model.server.handlers.IdsApiImpl
import org.modelix.model.server.handlers.ModelReplicationServer
import org.modelix.model.server.handlers.RepositoriesManager
import org.modelix.model.server.store.InMemoryStoreClient
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Duration.Companion.seconds

private val LOG = KotlinLogging.logger { }

class HistoryIndexPaginationTest {

    private lateinit var statistics: StoreClientWithStatistics
    private fun runTest(block: suspend ApplicationTestBuilder.() -> Unit) = testApplication {
        application {
            try {
                installDefaultServerPlugins()
                statistics = StoreClientWithStatistics(InMemoryStoreClient())
                val repoManager = RepositoriesManager(statistics)
                ModelReplicationServer(repoManager).init(this)
                IdsApiImpl(repoManager).init(this)
            } catch (ex: Throwable) {
                LOG.error("", ex)
            }
        }
        block()
    }

    @Test fun pagination_0_0() = runPaginationTest(0, 0)

    @Test fun pagination_0_1() = runPaginationTest(0, 1)

    @Test fun pagination_0_2() = runPaginationTest(0, 2)

    @Test fun pagination_0_3() = runPaginationTest(0, 3)

    @Test fun pagination_0_4() = runPaginationTest(0, 4)

    @Test fun pagination_0_5() = runPaginationTest(0, 5)

    @Test fun pagination_0_6() = runPaginationTest(0, 6)

    @Test fun pagination_0_7() = runPaginationTest(0, 7)

    @Test fun pagination_0_8() = runPaginationTest(0, 8)

    @Test fun pagination_0_9() = runPaginationTest(0, 9)

    @Test fun pagination_0_10() = runPaginationTest(0, 10)

    @Test fun pagination_10_10() = runPaginationTest(10, 10)

    @Test fun pagination_137_47() = runPaginationTest(137, 47)

    @Test fun pagination_138_47() = runPaginationTest(138, 47)

    @Test fun pagination_139_47() = runPaginationTest(139, 47)

    @Test fun pagination_140_47() = runPaginationTest(140, 47)

    @Test fun pagination_200_10() = runPaginationTest(200, 10)

    @Test fun pagination_201_10() = runPaginationTest(201, 10)

    @Test fun pagination_202_10() = runPaginationTest(202, 10)

    @Test fun pagination_201_201() = runPaginationTest(201, 201)

    private fun runPaginationTest(skip: Int, limit: Int) = runTest {
        val rand = Random(8923345)
        val modelClient: IModelClientV2 = createModelClient(lazyAndBlocking = true)
        val repositoryId = RepositoryId("repo1")
        val branchRef = repositoryId.getBranchReference()
        val initialVersion = modelClient.initRepository(RepositoryConfig(repositoryId = repositoryId.id, repositoryName = repositoryId.id, modelId = "61bd6cb0-33ff-45d8-9d1b-2149fdb01d16"))
        var currentVersion = initialVersion

        repeat(100) {
            run {
                val newVersion = IVersion.builder()
                    .baseVersion(currentVersion)
                    .tree(currentVersion.getModelTree())
                    .author("user1")
                    .time(currentVersion.getTimestamp()!! + rand.nextInt(0, 3).seconds)
                    .build()
                currentVersion = modelClient.push(branchRef, newVersion, currentVersion)
            }
            run {
                val newVersion = IVersion.builder()
                    .baseVersion(currentVersion)
                    .tree(currentVersion.getModelTree())
                    .author("user2")
                    .time(currentVersion.getTimestamp()!! + rand.nextInt(0, 3).seconds)
                    .build()
                currentVersion = modelClient.push(branchRef, newVersion, currentVersion)
            }
        }

        val expectedOrder = currentVersion.historyAsSequence().map { it.getObjectHash() }.toList()

        val history = modelClient.queryHistory(
            repositoryId = repositoryId,
            headVersion = currentVersion.getObjectHash(),
        ).range(
            skip = skip.toLong(),
            limit = limit.toLong(),
        )
        assertEquals(expectedOrder.drop(skip).take(limit).toSet(), history.map { it.versionHash }.toSet())
        assertEquals(expectedOrder.drop(skip).take(limit), history.map { it.versionHash })
    }
}
