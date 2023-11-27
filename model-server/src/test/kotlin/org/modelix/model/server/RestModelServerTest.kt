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

import org.junit.Assert
import org.junit.Test
import org.modelix.model.server.handlers.KeyValueLikeModelServer
import org.modelix.model.server.handlers.RepositoriesManager
import org.modelix.model.server.store.IStoreClient
import org.modelix.model.server.store.IgniteStoreClient
import org.modelix.model.server.store.InMemoryStoreClient
import org.modelix.model.server.store.LocalModelClient

abstract class RestModelServerTest {

    class Ignite : RestModelServerTest() {
        override fun createStoreClient() = IgniteStoreClient(inmemory = true)
    }

    class InMemoryMap : RestModelServerTest() {
        override fun createStoreClient() = InMemoryStoreClient()
    }

    protected abstract fun createStoreClient(): IStoreClient

    @Test
    fun testCollectUnexistingKey() {
        createStoreClient().use { storeClient ->
            val rms = KeyValueLikeModelServer(
                RepositoriesManager(LocalModelClient(storeClient)),
            )
            val result = rms.collect("unexistingKey")
            Assert.assertEquals(1, result.length().toLong())
            Assert.assertEquals(HashSet(mutableListOf("key")), result.getJSONObject(0).keySet())
            Assert.assertEquals("unexistingKey", result.getJSONObject(0)["key"])
        }
    }

    @Test
    fun testCollectExistingKeyNotHash() {
        createStoreClient().use { storeClient ->
            storeClient.put("existingKey", "foo", false)
            val rms = KeyValueLikeModelServer(
                RepositoriesManager(LocalModelClient(storeClient)),
            )
            val result = rms.collect("existingKey")
            Assert.assertEquals(1, result.length().toLong())
            Assert.assertEquals(
                HashSet(mutableListOf("key", "value")),
                result.getJSONObject(0).keySet(),
            )
            Assert.assertEquals("existingKey", result.getJSONObject(0)["key"])
            Assert.assertEquals("foo", result.getJSONObject(0)["value"])
        }
    }

    @Test
    fun testCollectExistingKeyHash() {
        createStoreClient().use { storeClient ->
            storeClient.put("existingKey", "hash-*0123456789-0123456789-0123456789-00001", false)
            storeClient.put("hash-*0123456789-0123456789-0123456789-00001", "bar", false)
            val rms = KeyValueLikeModelServer(
                RepositoriesManager(LocalModelClient(storeClient)),
            )
            val result = rms.collect("existingKey")
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
    }

    @Test
    fun testCollectExistingKeyHashChained() {
        createStoreClient().use { storeClient ->
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
            val rms = KeyValueLikeModelServer(
                RepositoriesManager(LocalModelClient(storeClient)),
            )
            val result = rms.collect("root")
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
    }

    @Test
    fun testCollectExistingKeyHashChainedWithRepetitions() {
        createStoreClient().use { storeClient ->
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
            val rms = KeyValueLikeModelServer(
                RepositoriesManager(LocalModelClient(storeClient)),
            )
            val result = rms.collect("root")
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
}
