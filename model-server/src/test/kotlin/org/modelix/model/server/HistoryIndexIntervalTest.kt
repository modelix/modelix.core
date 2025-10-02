package org.modelix.model.server

import io.ktor.server.testing.ApplicationTestBuilder
import io.ktor.server.testing.testApplication
import mu.KotlinLogging
import org.modelix.datastructures.history.HistoryInterval
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

class HistoryIndexIntervalTest {

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

    @Test fun interval_0_201_1() = runIntervalTest(0, 201, 1)

    @Test fun interval_0_201_10() = runIntervalTest(0, 201, 10)

    @Test fun interval_0_201_20() = runIntervalTest(0, 201, 20)

    @Test fun interval_0_201_50() = runIntervalTest(0, 201, 50)

    @Test fun interval_0_201_100() = runIntervalTest(0, 201, 100)

    @Test fun interval_0_201_150() = runIntervalTest(0, 201, 150)

    @Test fun interval_0_201_200() = runIntervalTest(0, 201, 200)

    @Test fun interval_0_201_500() = runIntervalTest(0, 201, 500)

    @Test fun interval_0_1_20() = runIntervalTest(0, 1, 20)

    @Test fun interval_0_2_20() = runIntervalTest(0, 2, 20)

    @Test fun interval_0_3_20() = runIntervalTest(0, 3, 20)

    @Test fun interval_0_4_20() = runIntervalTest(0, 4, 20)

    @Test fun interval_1_4_20() = runIntervalTest(1, 4, 20)

    @Test fun interval_2_4_20() = runIntervalTest(2, 4, 20)

    @Test fun interval_3_4_20() = runIntervalTest(3, 4, 20)

    @Test fun interval_4_4_20() = runIntervalTest(4, 4, 20)

    @Test fun interval_5_4_20() = runIntervalTest(5, 4, 20)

    private fun runIntervalTest(skip: Int, limit: Int, intervalSeconds: Int) = runTest {
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

        val expectedHistory = currentVersion.historyAsSequence().toList().reversed()

        val expectedIntervals = expectedHistory
            .groupBy { it.getTimestamp()!!.epochSeconds / intervalSeconds }
            .map { entry ->
                val versions = entry.value
                HistoryInterval(
                    firstVersionHash = versions.first().getObjectHash(),
                    lastVersionHash = versions.last().getObjectHash(),
                    size = versions.size.toLong(),
                    minTime = versions.minOf { it.getTimestamp()!! },
                    maxTime = versions.maxOf { it.getTimestamp()!! },
                    authors = versions.mapNotNull { it.getAuthor() }.toSet(),
                )
            }
            .reversed()
            .drop(skip)
            .take(limit)

        val timeRange = (expectedIntervals.minOf { it.minTime })..(expectedIntervals.maxOf { it.maxTime })
        val history = modelClient.queryHistory(repositoryId, currentVersion.getObjectHash()).intervals(
            timeRange = timeRange,
            interval = intervalSeconds.seconds,
        )
        assertEquals(expectedIntervals, history)
    }
}
