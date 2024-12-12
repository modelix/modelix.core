package org.modelix.model.server

import org.modelix.model.server.handlers.KeyValueLikeModelServer
import org.modelix.model.server.handlers.RepositoriesManager
import org.modelix.model.server.store.InMemoryStoreClient
import org.modelix.model.server.store.RequiresTransaction
import org.modelix.model.server.store.forGlobalRepository
import kotlin.test.Test
import kotlin.test.assertEquals

class RestModelServerTest {
    @Test
    fun testCollectUnexistingKey() {
        val store = InMemoryStoreClient()
        val rms = KeyValueLikeModelServer(RepositoriesManager(store))

        @OptIn(RequiresTransaction::class)
        val result = store.runRead { rms.collect("unexistingKey", null) }
        assertEquals(1, result.length().toLong())
        assertEquals(HashSet(mutableListOf("key")), result.getJSONObject(0).keySet())
        assertEquals("unexistingKey", result.getJSONObject(0)["key"])
    }

    @Test
    fun testCollectExistingKeyNotHash() {
        val genericStore = InMemoryStoreClient()
        val storeClient = genericStore.forGlobalRepository()
        @OptIn(RequiresTransaction::class)
        genericStore.runWriteTransaction { storeClient.put("existingKey", "foo", false) }
        val rms = KeyValueLikeModelServer(RepositoriesManager(genericStore))

        @OptIn(RequiresTransaction::class)
        val result = genericStore.runRead { rms.collect("existingKey", null) }
        assertEquals(1, result.length().toLong())
        assertEquals(
            HashSet(mutableListOf("key", "value")),
            result.getJSONObject(0).keySet(),
        )
        assertEquals("existingKey", result.getJSONObject(0)["key"])
        assertEquals("foo", result.getJSONObject(0)["value"])
    }

    @Test
    fun testCollectExistingKeyHash() {
        val genericStore = InMemoryStoreClient()
        val storeClient = genericStore.forGlobalRepository()
        @OptIn(RequiresTransaction::class)
        genericStore.runWrite {
            storeClient.put("existingKey", "hash-*0123456789-0123456789-0123456789-00001", false)
            storeClient.put("hash-*0123456789-0123456789-0123456789-00001", "bar", false)
        }
        val rms =
            KeyValueLikeModelServer(
                RepositoriesManager(genericStore),
            )

        @OptIn(RequiresTransaction::class)
        val result = genericStore.runRead { rms.collect("existingKey", null) }
        assertEquals(2, result.length().toLong())

        var obj = result.getJSONObject(0)
        assertEquals(HashSet(mutableListOf("key", "value")), obj.keySet())
        assertEquals("existingKey", obj["key"])
        assertEquals("hash-*0123456789-0123456789-0123456789-00001", obj["value"])

        obj = result.getJSONObject(1)
        assertEquals(HashSet(mutableListOf("key", "value")), obj.keySet())
        assertEquals("hash-*0123456789-0123456789-0123456789-00001", obj["key"])
        assertEquals("bar", obj["value"])
    }

    @Test
    fun testCollectExistingKeyHashChained() {
        val genericStore = InMemoryStoreClient()
        val storeClient = genericStore.forGlobalRepository()
        @OptIn(RequiresTransaction::class)
        genericStore.runWrite {
            storeClient.put("root", "hash-*0123456789-0123456789-0123456789-00001", false)
            storeClient.put(
                "hash-*0123456789-0123456789-0123456789-00001",
                "hash-*0123456789-0123456789-0123456789-00002",
                false,
            )
            storeClient.put(
                "hash-*0123456789-0123456789-0123456789-00002",
                "hash-*0123456789-0123456789-0123456789-00003",
                false,
            )
            storeClient.put(
                "hash-*0123456789-0123456789-0123456789-00003",
                "hash-*0123456789-0123456789-0123456789-00004",
                false,
            )
            storeClient.put("hash-*0123456789-0123456789-0123456789-00004", "end", false)
        }
        val rms =
            KeyValueLikeModelServer(
                RepositoriesManager(genericStore),
            )

        @OptIn(RequiresTransaction::class)
        val result = genericStore.runRead { rms.collect("root", null) }
        assertEquals(5, result.length().toLong())

        var obj = result.getJSONObject(0)
        assertEquals(HashSet(mutableListOf("key", "value")), obj.keySet())
        assertEquals("root", obj["key"])
        assertEquals("hash-*0123456789-0123456789-0123456789-00001", obj["value"])

        obj = result.getJSONObject(1)
        assertEquals(HashSet(mutableListOf("key", "value")), obj.keySet())
        assertEquals("hash-*0123456789-0123456789-0123456789-00001", obj["key"])
        assertEquals("hash-*0123456789-0123456789-0123456789-00002", obj["value"])

        obj = result.getJSONObject(2)
        assertEquals(HashSet(mutableListOf("key", "value")), obj.keySet())
        assertEquals("hash-*0123456789-0123456789-0123456789-00002", obj["key"])
        assertEquals("hash-*0123456789-0123456789-0123456789-00003", obj["value"])

        obj = result.getJSONObject(3)
        assertEquals(HashSet(mutableListOf("key", "value")), obj.keySet())
        assertEquals("hash-*0123456789-0123456789-0123456789-00003", obj["key"])
        assertEquals("hash-*0123456789-0123456789-0123456789-00004", obj["value"])

        obj = result.getJSONObject(4)
        assertEquals(HashSet(mutableListOf("key", "value")), obj.keySet())
        assertEquals("hash-*0123456789-0123456789-0123456789-00004", obj["key"])
        assertEquals("end", obj["value"])
    }

    @Test
    fun testCollectExistingKeyHashChainedWithRepetitions() {
        val genericStore = InMemoryStoreClient()
        val storeClient = genericStore.forGlobalRepository()
        @OptIn(RequiresTransaction::class)
        genericStore.runWrite {
            storeClient.put("root", "hash-*0123456789-0123456789-0123456789-00001", false)
            storeClient.put(
                "hash-*0123456789-0123456789-0123456789-00001",
                "hash-*0123456789-0123456789-0123456789-00002",
                false,
            )
            storeClient.put(
                "hash-*0123456789-0123456789-0123456789-00002",
                "hash-*0123456789-0123456789-0123456789-00003",
                false,
            )
            storeClient.put(
                "hash-*0123456789-0123456789-0123456789-00003",
                "hash-*0123456789-0123456789-0123456789-00001",
                false,
            )
        }
        val rms =
            KeyValueLikeModelServer(
                RepositoriesManager(genericStore),
            )

        @OptIn(RequiresTransaction::class)
        val result = genericStore.runRead { rms.collect("root", null) }
        assertEquals(4, result.length().toLong())

        var obj = result.getJSONObject(0)
        assertEquals(HashSet(mutableListOf("key", "value")), obj.keySet())
        assertEquals("root", obj["key"])
        assertEquals("hash-*0123456789-0123456789-0123456789-00001", obj["value"])

        obj = result.getJSONObject(1)
        assertEquals(HashSet(mutableListOf("key", "value")), obj.keySet())
        assertEquals("hash-*0123456789-0123456789-0123456789-00001", obj["key"])
        assertEquals("hash-*0123456789-0123456789-0123456789-00002", obj["value"])

        obj = result.getJSONObject(2)
        assertEquals(HashSet(mutableListOf("key", "value")), obj.keySet())
        assertEquals("hash-*0123456789-0123456789-0123456789-00002", obj["key"])
        assertEquals("hash-*0123456789-0123456789-0123456789-00003", obj["value"])

        obj = result.getJSONObject(3)
        assertEquals(HashSet(mutableListOf("key", "value")), obj.keySet())
        assertEquals("hash-*0123456789-0123456789-0123456789-00003", obj["key"])
        assertEquals("hash-*0123456789-0123456789-0123456789-00001", obj["value"])
    }
}
