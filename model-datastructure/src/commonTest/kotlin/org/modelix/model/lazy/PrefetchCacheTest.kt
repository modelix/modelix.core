import org.modelix.model.lazy.AccessTrackingStore
import org.modelix.model.lazy.NonCachingObjectStore
import org.modelix.model.lazy.ObjectStoreCache
import org.modelix.model.lazy.PrefetchCache
import org.modelix.model.lazy.WrittenEntry
import org.modelix.model.persistent.CPNode
import org.modelix.model.persistent.MapBasedStore
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class PrefetchCacheTest {

    private val keyValueStore = MapBasedStore()

    @Test
    fun nullValueIsNotCached() {
        val deserializingKeyValueStore = NonCachingObjectStore(keyValueStore)
        val prefetchCache = PrefetchCache(deserializingKeyValueStore)
        prefetchCache["key", { value -> value }]
        keyValueStore.put("key", "value")

        val result = prefetchCache["key", { value -> value }]

        assertEquals("value", result)
    }

    @Test
    fun valuesAreCachedWhenGettingMultipleEntriesAsIterable() {
        val accessTrackingKeyValueStore = AccessTrackingStore(keyValueStore)
        val deserializingKeyValueStore = NonCachingObjectStore(accessTrackingKeyValueStore)
        val prefetchCache = PrefetchCache(deserializingKeyValueStore)
        keyValueStore.put("key", "value")
        prefetchCache.getAll(listOf("key")) { _, value -> value }
        accessTrackingKeyValueStore.accessedEntries.clear()

        val result = prefetchCache.getAll(listOf("key")) { _, value -> value }

        assertEquals(listOf("value"), result)
        assertTrue(accessTrackingKeyValueStore.accessedEntries.isEmpty())
    }

    @Test
    fun valuesAreCachedWhenGettingMultipleEntriesAsMap() {
        val accessTrackingKeyValueStore = AccessTrackingStore(keyValueStore)
        val deserializingKeyValueStore = NonCachingObjectStore(accessTrackingKeyValueStore)
        val prefetchCache = PrefetchCache(deserializingKeyValueStore)
        val regularKey = "regularKey"
        val prefetchKey = "prefetchKey"
        val nodeForRegularKey = CPNode.create(
            2, null, 1, null, LongArray(0),
            emptyArray(), emptyArray(), emptyArray(), emptyArray(),
        )
        val nodeForPrefetchKey = CPNode.create(
            3, null, 1, null, LongArray(0),
            emptyArray(), emptyArray(), emptyArray(), emptyArray(),
        )
        keyValueStore.putAll(mapOf(regularKey to nodeForRegularKey.serialize(), prefetchKey to nodeForPrefetchKey.serialize()))
        val regularKeyReference = WrittenEntry(regularKey) { nodeForRegularKey }
        val prefetchKeyReference = WrittenEntry(prefetchKey) { nodeForPrefetchKey }
        prefetchCache.getAll(listOf(regularKeyReference), listOf(prefetchKeyReference))
        accessTrackingKeyValueStore.accessedEntries.clear()

        val result = prefetchCache.getAll(listOf(regularKeyReference), listOf(prefetchKeyReference))

        assertEquals(mapOf(regularKey to nodeForRegularKey, prefetchKey to nodeForPrefetchKey), result)
        assertTrue(accessTrackingKeyValueStore.accessedEntries.isEmpty())
    }

    @Test
    fun nullValuesAreNotCachedWhenGettingMultipleEntriesAsMap() {
        val deserializingKeyValueStore = ObjectStoreCache(keyValueStore)
        val prefetchCache = PrefetchCache(deserializingKeyValueStore)
        val regularKey = "regularKey"
        val prefetchKey = "prefetchKey"
        val nodeForRegularKey = CPNode.create(
            2, null, 1, null, LongArray(0),
            emptyArray(), emptyArray(), emptyArray(), emptyArray(),
        )
        val nodeForPrefetchKey = CPNode.create(
            3, null, 1, null, LongArray(0),
            emptyArray(), emptyArray(), emptyArray(), emptyArray(),
        )
        val regularKeyReference = WrittenEntry(regularKey) { nodeForRegularKey }
        val prefetchKeyReference = WrittenEntry(prefetchKey) { nodeForPrefetchKey }
        prefetchCache.getAll(listOf(regularKeyReference), listOf(prefetchKeyReference))
        keyValueStore.putAll(mapOf(regularKey to nodeForRegularKey.serialize(), prefetchKey to nodeForPrefetchKey.serialize()))

        val result = prefetchCache.getAll(listOf(regularKeyReference), listOf(prefetchKeyReference))

        assertEquals(mapOf(regularKey to nodeForRegularKey, prefetchKey to nodeForPrefetchKey), result)
    }
}
