package org.modelix.model.server

import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import org.modelix.model.IKeyListener
import org.modelix.model.server.store.IStoreClient
import org.modelix.model.server.store.IgniteStoreClient
import org.modelix.model.server.store.InMemoryStoreClient
import org.modelix.model.server.store.MissingWriteTransactionException
import org.modelix.model.server.store.forGlobalRepository
import java.util.Collections
import kotlin.random.Random
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class MapBasedStoreClientTest : StoreClientTest(InMemoryStoreClient().forGlobalRepository())
class IgniteStoreClientTest : StoreClientTest(IgniteStoreClient(inmemory = true).forGlobalRepository())

abstract class StoreClientTest(val store: IStoreClient) {

    @AfterTest
    fun cleanup() {
        store.close()
    }

    @Test
    fun `transaction can be started from inside a transaction`() {
        store.runWriteTransaction {
            store.runWriteTransaction {
                store.put("abc", "def")
            }
        }
    }

    @Test
    fun `put without transaction fails`() {
        val key = "ljnrdlfkesmgf"
        val value = "izujztdrsew"
        assertFailsWith<MissingWriteTransactionException> {
            store.put(key, value)
        }
    }

    @Test
    fun `transactions are isolated`() = runTest {
        val key = "kjhndsweret"
        repeat(2) {
            val rand = Random(it)
            launch {
                store.runWriteTransaction {
                    repeat(10) {
                        val value = rand.nextInt().toString()
                        store.put(key, value)
                        assertEquals(value, store.get(key))
                        Thread.sleep(rand.nextLong(5, 10))
                        assertEquals(value, store.get(key))
                    }
                }
            }
        }
    }

    @Test
    fun `transactions are atomic`() = runTest {
        val key = "ioudgbnr"
        val value1 = "a"
        val value2 = "b"

        store.runWriteTransaction { store.put(key, value1) }
        assertEquals(value1, store.runReadTransaction { store.get(key) })
        assertFailsWith(NullPointerException::class) {
            store.runWriteTransaction {
                store.put(key, value2)
                assertEquals(value2, store.get(key))
                throw NullPointerException()
            }
        }
        assertEquals(value1, store.runReadTransaction { store.get(key) }) // failed transaction should be rolled back
    }

    @Test
    fun `listeners don't see incomplete transaction`() = runTest {
        val key = "nbmndsyr"
        val value1 = "a"
        val value2 = "b"
        val value3 = "c"

        val valuesSeenByListener = Collections.synchronizedSet(HashSet<String?>())
        store.listen(
            key,
            object : IKeyListener {
                override fun changed(key: String, value: String?) {
                    valuesSeenByListener += value
                    valuesSeenByListener += store.get(key)
                }
            },
        )

        store.runWriteTransaction { store.put(key, value1) }
        assertEquals(value1, store.runReadTransaction { store.get(key) })

        assertEquals(setOf<String?>(value1), valuesSeenByListener)
        valuesSeenByListener.clear()

        coroutineScope {
            launch {
                assertFailsWith(NullPointerException::class) {
                    store.runWriteTransaction {
                        assertEquals(value1, store.get(key))
                        store.put(key, value2, silent = false)
                        assertEquals(value2, store.get(key))
                        throw NullPointerException()
                    }
                }
            }

            launch {
                store.runWriteTransaction {
                    assertEquals(value1, store.get(key))
                    store.put(key, value3, silent = false)
                    assertEquals(value3, store.get(key))
                }
            }
        }

        assertEquals(setOf<String?>(value3), valuesSeenByListener)
    }
}
