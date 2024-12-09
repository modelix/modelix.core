package org.modelix.model.server

import org.junit.Assert
import org.junit.Test
import org.modelix.model.server.handlers.KeyValueLikeModelServer
import org.modelix.model.server.handlers.RepositoriesManager
import org.modelix.model.server.store.InMemoryStoreClient
import org.modelix.model.server.store.forGlobalRepository
import org.modelix.model.server.store.getGenericStore

class RestModelServerTest {
    @Test
    fun testCollectUnexistingKey() {
        val rms = KeyValueLikeModelServer(RepositoriesManager(InMemoryStoreClient()))
        val result = rms.collect("unexistingKey", null)
        Assert.assertEquals(1, result.length().toLong())
        Assert.assertEquals(HashSet(mutableListOf("key")), result.getJSONObject(0).keySet())
        Assert.assertEquals("unexistingKey", result.getJSONObject(0)["key"])
    }

    @Test
    fun testCollectExistingKeyNotHash() {
        val storeClient = InMemoryStoreClient().forGlobalRepository()
        storeClient.put("existingKey", "foo", false)
        val rms = KeyValueLikeModelServer(RepositoriesManager(storeClient.getGenericStore()))
        val result = rms.collect("existingKey", null)
        Assert.assertEquals(1, result.length().toLong())
        Assert.assertEquals(
            HashSet(mutableListOf("key", "value")),
            result.getJSONObject(0).keySet(),
        )
        Assert.assertEquals("existingKey", result.getJSONObject(0)["key"])
        Assert.assertEquals("foo", result.getJSONObject(0)["value"])
    }

    @Test
    fun testCollectExistingKeyHash() {
        val storeClient = InMemoryStoreClient().forGlobalRepository()
        storeClient.put("existingKey", "hash-*0123456789-0123456789-0123456789-00001", false)
        storeClient.put("hash-*0123456789-0123456789-0123456789-00001", "bar", false)
        val rms =
            KeyValueLikeModelServer(
                RepositoriesManager(storeClient.getGenericStore()),
            )
        val result = rms.collect("existingKey", null)
        Assert.assertEquals(2, result.length().toLong())

        var obj = result.getJSONObject(0)
        Assert.assertEquals(HashSet(mutableListOf("key", "value")), obj.keySet())
        Assert.assertEquals("existingKey", obj["key"])
        Assert.assertEquals("hash-*0123456789-0123456789-0123456789-00001", obj["value"])

        obj = result.getJSONObject(1)
        Assert.assertEquals(HashSet(mutableListOf("key", "value")), obj.keySet())
        Assert.assertEquals("hash-*0123456789-0123456789-0123456789-00001", obj["key"])
        Assert.assertEquals("bar", obj["value"])
    }

    @Test
    fun testCollectExistingKeyHashChained() {
        val storeClient = InMemoryStoreClient().forGlobalRepository()
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
        val rms =
            KeyValueLikeModelServer(
                RepositoriesManager(storeClient.getGenericStore()),
            )
        val result = rms.collect("root", null)
        Assert.assertEquals(5, result.length().toLong())

        var obj = result.getJSONObject(0)
        Assert.assertEquals(HashSet(mutableListOf("key", "value")), obj.keySet())
        Assert.assertEquals("root", obj["key"])
        Assert.assertEquals("hash-*0123456789-0123456789-0123456789-00001", obj["value"])

        obj = result.getJSONObject(1)
        Assert.assertEquals(HashSet(mutableListOf("key", "value")), obj.keySet())
        Assert.assertEquals("hash-*0123456789-0123456789-0123456789-00001", obj["key"])
        Assert.assertEquals("hash-*0123456789-0123456789-0123456789-00002", obj["value"])

        obj = result.getJSONObject(2)
        Assert.assertEquals(HashSet(mutableListOf("key", "value")), obj.keySet())
        Assert.assertEquals("hash-*0123456789-0123456789-0123456789-00002", obj["key"])
        Assert.assertEquals("hash-*0123456789-0123456789-0123456789-00003", obj["value"])

        obj = result.getJSONObject(3)
        Assert.assertEquals(HashSet(mutableListOf("key", "value")), obj.keySet())
        Assert.assertEquals("hash-*0123456789-0123456789-0123456789-00003", obj["key"])
        Assert.assertEquals("hash-*0123456789-0123456789-0123456789-00004", obj["value"])

        obj = result.getJSONObject(4)
        Assert.assertEquals(HashSet(mutableListOf("key", "value")), obj.keySet())
        Assert.assertEquals("hash-*0123456789-0123456789-0123456789-00004", obj["key"])
        Assert.assertEquals("end", obj["value"])
    }

    @Test
    fun testCollectExistingKeyHashChainedWithRepetitions() {
        val storeClient = InMemoryStoreClient().forGlobalRepository()
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
        val rms =
            KeyValueLikeModelServer(
                RepositoriesManager(storeClient.getGenericStore()),
            )
        val result = rms.collect("root", null)
        Assert.assertEquals(4, result.length().toLong())

        var obj = result.getJSONObject(0)
        Assert.assertEquals(HashSet(mutableListOf("key", "value")), obj.keySet())
        Assert.assertEquals("root", obj["key"])
        Assert.assertEquals("hash-*0123456789-0123456789-0123456789-00001", obj["value"])

        obj = result.getJSONObject(1)
        Assert.assertEquals(HashSet(mutableListOf("key", "value")), obj.keySet())
        Assert.assertEquals("hash-*0123456789-0123456789-0123456789-00001", obj["key"])
        Assert.assertEquals("hash-*0123456789-0123456789-0123456789-00002", obj["value"])

        obj = result.getJSONObject(2)
        Assert.assertEquals(HashSet(mutableListOf("key", "value")), obj.keySet())
        Assert.assertEquals("hash-*0123456789-0123456789-0123456789-00002", obj["key"])
        Assert.assertEquals("hash-*0123456789-0123456789-0123456789-00003", obj["value"])

        obj = result.getJSONObject(3)
        Assert.assertEquals(HashSet(mutableListOf("key", "value")), obj.keySet())
        Assert.assertEquals("hash-*0123456789-0123456789-0123456789-00003", obj["key"])
        Assert.assertEquals("hash-*0123456789-0123456789-0123456789-00001", obj["value"])
    }
}
