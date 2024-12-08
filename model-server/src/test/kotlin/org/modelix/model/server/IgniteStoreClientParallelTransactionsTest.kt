package org.modelix.model.server

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import org.modelix.model.IKeyListener
import org.modelix.model.server.store.IStoreClient
import org.modelix.model.server.store.IgniteStoreClient
import org.modelix.model.server.store.InMemoryStoreClient
import org.modelix.model.server.store.forGlobalRepository
import org.slf4j.LoggerFactory
import java.util.concurrent.CompletableFuture
import java.util.concurrent.TimeUnit
import kotlin.test.AfterTest
import kotlin.test.Ignore
import kotlin.test.Test
import kotlin.test.assertEquals

@Ignore("Doesn't support parallel transactions (yet)")
class MapBasedStoreClientParallelTransactionsTest : StoreClientParallelTransactionsTest(InMemoryStoreClient().forGlobalRepository())

class IgniteStoreClientParallelTransactionsTest : StoreClientParallelTransactionsTest(IgniteStoreClient(inmemory = true).forGlobalRepository())

abstract class StoreClientParallelTransactionsTest(val store: IStoreClient) {

    @AfterTest
    fun cleanup() {
        store.close()
    }

    @Ignore("write transactions cannot run in parallel anymore")
    @Test
    fun `notifications are not lost because of parallel transactions`() = runTest {
        val threadBlocker = ThreadBlocker()
        val notifiedValueFuture = CompletableFuture<String>()

        store.listen(
            "key2",
            object : IKeyListener {
                override fun changed(key: String, value: String?) {
                    notifiedValueFuture.complete(store[key]!!)
                }
            },
        )

        launch(Dispatchers.IO) {
            store.runWriteTransaction {
                store.put("key1", "valueA")
                threadBlocker.reachPointInTime(1)
                threadBlocker.sleepUntilPointInTime(2)
                // The bug was originally caused here.
                // After the first transaction finished,
                // it tried nothing about pending changes from the second transaction
                // that was not finished yet.
            }
            threadBlocker.reachPointInTime(3)
        }

        launch(Dispatchers.IO) {
            store.runWriteTransaction {
                threadBlocker.sleepUntilPointInTime(1)
                store.put("key2", "valueB")
                threadBlocker.reachPointInTime(2)
                threadBlocker.sleepUntilPointInTime(3)
            }
        }

        threadBlocker.sleepUntilPointInTime(3)
        val notifiedValue = notifiedValueFuture.get(10, TimeUnit.SECONDS)
        assertEquals("valueB", notifiedValue)
    }

    @Test
    fun `non-ordered writes don't result in a deadlock`() = runTest {
        // fine-grained locks always have the potential to cause a deadlock when they are not acquired in the
        // same order (lock ordering).
        // Ignite manages locks per entry. We already sort the entries in putAll, but that's not sufficient because
        // There can still be multiple put calls.
        // This test reproduces this issue which is now fixed by preventing parallel write transactions.

        val threadBlocker = ThreadBlocker()

        launch(Dispatchers.IO) {
            println("(1) launched")
            threadBlocker.reachPointInTime(1)
            threadBlocker.sleepUntilPointInTime(2)
            store.runWriteTransaction {
                println("(1) inside transaction")

                println("(1) writing a = c1")
                store.put("a", "c1")
                println("(1) done a = c1")

                Thread.sleep(100)

                println("(1) writing b = c1")
                store.put("b", "c1")
                println("(1) done b = c1")
            }
        }

        launch(Dispatchers.IO) {
            println("(2) launched")
            threadBlocker.sleepUntilPointInTime(1)
            threadBlocker.reachPointInTime(2)
            store.runWriteTransaction {
                println("(2) inside transaction")

                println("(2) writing b = c2")
                store.put("b", "c2")
                println("(1) done b = c2")

                Thread.sleep(100)

                println("(2) writing a = c2")
                store.put("a", "c2")
                println("(2) done a = c2")
            }
        }
    }
}

class ThreadBlocker {

    companion object {
        private val LOG = LoggerFactory.getLogger(ThreadBlocker::class.java)
    }

    private var reachedPointInTime = 0

    @Synchronized
    fun reachPointInTime(pointInTime: Int) {
        reachedPointInTime = pointInTime
        LOG.debug("Reached point in time {}", reachedPointInTime)
    }

    fun sleepUntilPointInTime(pointInTime: Int) {
        LOG.debug("Waiting for point in time {}", pointInTime)
        sleepUntil {
            reachedPointInTime >= pointInTime
        }
    }
}

fun sleepUntil(
    checkIntervalMilliseconds: Long = 10,
    timeoutMilliseconds: Long = 5_000,
    condition: () -> Boolean,
) {
    check(checkIntervalMilliseconds > 0) {
        "checkIntervalMilliseconds must be positive."
    }
    check(timeoutMilliseconds > 0) {
        "timeoutMilliseconds must be positive."
    }
    var remainingDelays = timeoutMilliseconds / checkIntervalMilliseconds
    while (!condition()) {
        if (remainingDelays == 0L) {
            error("Waited too long.")
        }
        Thread.sleep(checkIntervalMilliseconds)
        remainingDelays--
    }
}
