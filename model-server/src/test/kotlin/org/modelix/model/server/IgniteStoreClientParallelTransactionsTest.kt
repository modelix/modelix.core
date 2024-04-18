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

import ThreadBlocker
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import org.modelix.model.IKeyListener
import org.modelix.model.server.store.IStoreClient
import org.modelix.model.server.store.IgniteStoreClient
import org.modelix.model.server.store.InMemoryStoreClient
import org.modelix.model.server.store.runTransactionSuspendable
import java.util.concurrent.CompletableFuture
import java.util.concurrent.TimeUnit
import kotlin.test.AfterTest
import kotlin.test.Ignore
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals

@Ignore("Doesn't support parallel transactions (yet)")
class MabBasedStoreClientParallelTransactionsTest : StoreClientParallelTransactionsTest(InMemoryStoreClient())

class IgniteStoreClientParallelTransactionsTest : StoreClientParallelTransactionsTest(IgniteStoreClient(inmemory = true))

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

    @Test
    fun parallelSuspendableTransactionsRunOnDifferentThreads() = runTest {
        var threadIdA1: Long? = null
        var threadIdA2: Long? = null
        var threadIdA3: Long? = null
        var threadIdB1: Long? = null
        var threadIdB2: Long? = null
        var threadIdB3: Long? = null
        fun getThreadId(): Long {
            return Thread.currentThread().id
        }
        val threadBlocker = ThreadBlocker()

        val jobA = launch(Dispatchers.IO) {
            store.runTransactionSuspendable {
                threadIdA1 = getThreadId()
                runBlocking {
                    store.runTransactionSuspendable {
                        threadIdA2 = getThreadId()
                        threadBlocker.sleepUntilPointInTime(1)
                        runBlocking {
                            store.runTransactionSuspendable {
                                threadIdA3 = getThreadId()
                            }
                        }
                    }
                }
            }
        }
        val jobB = launch(Dispatchers.IO) {
            store.runTransactionSuspendable {
                threadIdB1 = getThreadId()
                threadBlocker.reachPointInTime(1)
                runBlocking {
                    store.runTransactionSuspendable {
                        threadIdB2 = getThreadId()
                        runBlocking {
                            store.runTransactionSuspendable {
                                threadIdB3 = getThreadId()
                            }
                        }
                    }
                }
            }
        }
        jobA.join()
        jobB.join()

        assertNotEquals(threadIdA1, threadIdB2)
        assertEquals(threadIdA1, threadIdA2)
        assertEquals(threadIdA1, threadIdA3)
        assertEquals(threadIdB1, threadIdB2)
        assertEquals(threadIdB1, threadIdB3)
    }
}
