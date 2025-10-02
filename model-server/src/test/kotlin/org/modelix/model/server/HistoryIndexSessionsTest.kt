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
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

private val LOG = KotlinLogging.logger { }

class HistoryIndexSessionsTest {

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

    @Test fun interval_0_201_1() = runIntervalTest(0, 201, 1.minutes)

    @Test fun interval_0_201_2() = runIntervalTest(0, 201, 2.minutes)

    @Test fun interval_0_201_3() = runIntervalTest(0, 201, 3.minutes)

    @Test fun interval_0_201_4() = runIntervalTest(0, 201, 4.minutes)

    @Test fun interval_0_201_5() = runIntervalTest(0, 201, 5.minutes)

    @Test fun interval_0_201_6() = runIntervalTest(0, 201, 6.minutes)

    @Test fun interval_0_201_7() = runIntervalTest(0, 201, 7.minutes)

    @Test fun interval_0_201_8() = runIntervalTest(0, 201, 8.minutes)

    @Test fun interval_0_201_9() = runIntervalTest(0, 201, 9.minutes)

    @Test fun interval_0_201_10() = runIntervalTest(0, 201, 10.minutes)

    @Test fun interval_0_201_11() = runIntervalTest(0, 201, 11.minutes)

    @Test fun interval_0_1_5() = runIntervalTest(0, 1, 5.minutes)

    @Test fun interval_0_2_5() = runIntervalTest(0, 2, 5.minutes)

    @Test fun interval_0_3_5() = runIntervalTest(0, 3, 5.minutes)

    @Test fun interval_0_4_5() = runIntervalTest(0, 4, 5.minutes)

    @Test fun interval_1_4_5() = runIntervalTest(1, 4, 5.minutes)

    @Test fun interval_2_4_5() = runIntervalTest(2, 4, 5.minutes)

    @Test fun interval_3_4_5() = runIntervalTest(3, 4, 5.minutes)

    @Test fun interval_4_4_5() = runIntervalTest(4, 4, 5.minutes)

    @Test fun interval_5_4_5() = runIntervalTest(5, 4, 5.minutes)

    private fun runIntervalTest(skip: Int, limit: Int, delay: Duration) = runTest {
        val rand = Random(8923345)
        val modelClient: IModelClientV2 = createModelClient(lazyAndBlocking = true)
        val repositoryId = RepositoryId("repo1")
        val branchRef = repositoryId.getBranchReference()
        val initialVersion = modelClient.initRepository(RepositoryConfig(repositoryId = repositoryId.id, repositoryName = repositoryId.id, modelId = "61bd6cb0-33ff-45d8-9d1b-2149fdb01d16"))
        var currentVersion = initialVersion

        var nextTimestamp = currentVersion.getTimestamp()!! + rand.nextInt(0, 3).seconds
        repeat(10) {
            repeat(10) {
                run {
                    val newVersion = IVersion.builder()
                        .baseVersion(currentVersion)
                        .tree(currentVersion.getModelTree())
                        .author("user1")
                        .time(nextTimestamp)
                        .build()
                    currentVersion = modelClient.push(branchRef, newVersion, currentVersion)
                }
                nextTimestamp += rand.nextInt(0, 3).seconds
                run {
                    val newVersion = IVersion.builder()
                        .baseVersion(currentVersion)
                        .tree(currentVersion.getModelTree())
                        .author("user2")
                        .time(nextTimestamp)
                        .build()
                    currentVersion = modelClient.push(branchRef, newVersion, currentVersion)
                }
                nextTimestamp += rand.nextInt(0, 3).seconds
            }
            nextTimestamp += rand.nextInt(1 * 60, 10 * 60).seconds
        }

        val expectedHistory = currentVersion.historyAsSequence().toList().reversed()

        val expectedIntervals = expectedHistory.fold(listOf<HistoryInterval>()) { acc, version ->
            if (acc.isEmpty() || version.getTimestamp()!! - acc.last().maxTime >= delay) {
                acc + HistoryInterval(
                    firstVersionHash = version.getObjectHash(),
                    lastVersionHash = version.getObjectHash(),
                    size = 1,
                    minTime = version.getTimestamp()!!,
                    maxTime = version.getTimestamp()!!,
                    authors = setOfNotNull(version.getAuthor()),
                )
            } else {
                val lastInterval = acc.last()
                acc.dropLast(1) + HistoryInterval(
                    firstVersionHash = lastInterval.firstVersionHash,
                    lastVersionHash = version.getObjectHash(),
                    size = lastInterval.size + 1,
                    minTime = lastInterval.minTime,
                    maxTime = version.getTimestamp()!!,
                    authors = lastInterval.authors + listOfNotNull(version.getAuthor()),
                )
            }
        }.reversed().drop(skip).take(limit)

        val timeRange = (expectedIntervals.minOf { it.minTime })..(expectedIntervals.maxOf { it.maxTime })
        val history = modelClient.queryHistory(
            repositoryId = repositoryId,
            headVersion = currentVersion.getObjectHash(),
        ).sessions(
            timeRange = timeRange,
            delay = delay,
        )
        assertEquals(expectedIntervals, history)
    }
}
