/*
 * Copyright (c) 2023.
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
class MabBasedStoreClientParallelTransactionsTest : StoreClientParallelTransactionsTest(InMemoryStoreClient().forGlobalRepository())

class IgniteStoreClientParallelTransactionsTest : StoreClientParallelTransactionsTest(IgniteStoreClient(inmemory = true).forGlobalRepository())

abstract class StoreClientParallelTransactionsTest(val store: IStoreClient) {

    @AfterTest
    fun cleanup() {
        store.close()
    }

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
            store.runTransaction {
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
            store.runTransaction {
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
    timeoutMilliseconds: Long = 1000,
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
