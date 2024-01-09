/*
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *  http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

import org.modelix.model.lazy.KVEntryReference
import org.modelix.model.lazy.ObjectStoreCache
import org.modelix.model.lazy.ref
import org.modelix.model.persistent.CPHamtInternal
import org.modelix.model.persistent.CPHamtNode
import org.modelix.model.persistent.CPNode
import org.modelix.model.persistent.MapBaseStore
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals

class HamtTest {
    @Test
    fun test_random() {
        val rand = Random(1)
        val expectedMap: MutableMap<Long, Long> = HashMap()
        val store = MapBaseStore()
        val storeCache = ObjectStoreCache(store)
        var hamt: CPHamtNode? = CPHamtInternal.createEmpty()
        for (i in 0..999) {
            if (expectedMap.isEmpty() || rand.nextBoolean()) {
                // add entry
                val key = rand.nextInt(1000).toLong()
                val value = rand.nextLong()
                hamt = hamt!!.put(key, createEntry(value), storeCache)
                expectedMap[key] = value
            } else {
                val keys: List<Long> = ArrayList(expectedMap.keys)
                val key = keys[rand.nextInt(keys.size)]
                if (rand.nextBoolean()) {
                    // remove entry
                    hamt = hamt!!.remove(key, storeCache)
                    expectedMap.remove(key)
                } else {
                    // replace entry
                    val value = rand.nextLong()
                    hamt = hamt!!.put(key, createEntry(value), storeCache)
                    expectedMap[key] = value
                }
            }
            storeCache.clearCache()
            for ((key, value) in expectedMap) {
                assertEquals(value, hamt!!.get(key, storeCache)!!.getValue(storeCache).id)
            }
        }
    }

    private fun createEntry(id: Long) = createNode(id).ref()

    private fun createNode(id: Long) = CPNode.create(
        id,
        null,
        0,
        null,
        longArrayOf(),
        arrayOf(),
        arrayOf(),
        arrayOf(),
        arrayOf(),
    )

    @Test
    fun test_random_case_causing_outofbounds_on_js() {
        val store = MapBaseStore()
        val storeCache = ObjectStoreCache(store)
        var hamt: CPHamtNode? = CPHamtInternal.createEmpty()
        var getId = { e: KVEntryReference<CPNode>? -> e!!.getValue(storeCache).id }

        hamt = hamt!!.put(965L, createEntry(-6579471327666419615), storeCache)
        hamt = hamt!!.put(949L, createEntry(4912341421267007347), storeCache)
        assertEquals(4912341421267007347, getId(hamt!!.get(949L, storeCache)))
        hamt = hamt!!.put(260L, createEntry(4166750678024106842), storeCache)
        assertEquals(4166750678024106842, getId(hamt!!.get(260L, storeCache)))
        hamt = hamt!!.put(794L, createEntry(5492533034562136353), storeCache)
        hamt = hamt!!.put(104L, createEntry(-6505928823483070382), storeCache)
        hamt = hamt!!.put(47L, createEntry(3122507882718949737), storeCache)
        hamt = hamt!!.put(693L, createEntry(-2086105010854963537), storeCache)
        storeCache.clearCache()
        // assertEquals(69239088, (hamt!!.getData() as CPHamtInternal).bitmap)
        // assertEquals(6, (hamt!!.getData() as CPHamtInternal).children.count())
        assertEquals(-2086105010854963537, getId(hamt!!.get(693L, storeCache)))
    }

    /**
     * It's important that all clients end up with the same version hash even if they apply the same conflict free
     * operations, but just in a different order. This allows them to sync their replica of the model by just applying
     * the operations of a new version instead of downloading the new snapshot (which would require multiple requests).
     */
    @Test
    fun insertionOrderTest() {
        val store = ObjectStoreCache(MapBaseStore())
        val emptyMap = CPHamtInternal.createEmpty()

        val rand = Random(123456789L)
        val entries = HashMap<Long, KVEntryReference<CPNode>>()
        for (i in 1..10) {
            for (k in 1..500) {
                val id = i * 1_000_000L + k
                entries[id] = createEntry(id)
            }
        }
        val keysToRemove = entries.keys.shuffled(rand).take(1000)

        var expectedHash: String? = null

        for (i in 1..10) {
            var map: CPHamtNode = emptyMap
            entries.entries.shuffled(rand).forEach { map = map.put(it.key, it.value, store)!! }
            keysToRemove.forEach { map = map.remove(it, store)!! }
            val hash = map.hash
            if (i == 1) {
                expectedHash = hash
            } else {
                assertEquals(expectedHash!!, hash)
            }
        }
    }
}
