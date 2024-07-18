import org.modelix.model.lazy.AccessTrackingStore
import org.modelix.model.lazy.NonCachingObjectStore
import org.modelix.model.lazy.PrefetchCache
import org.modelix.model.lazy.WrittenEntry
import org.modelix.model.persistent.CPNode
import org.modelix.model.persistent.MapBasedStore
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class PrefetchCacheTest {

    private val keyValueStore = MapBasedStore()
    private val accessTrackingKeyValueStore = AccessTrackingStore(keyValueStore)
    private val deserializingKeyValueStore = NonCachingObjectStore(accessTrackingKeyValueStore)
    private val prefetchCache = PrefetchCache(deserializingKeyValueStore)

    @Test
    fun entriesAreCachedAfterGettingMultipleEntriesAsIterable() {
        keyValueStore.put("key", "value")
        prefetchCache.getAll(listOf("key")) { _, value -> value }
        accessTrackingKeyValueStore.accessedEntries.clear()

        val result = prefetchCache.getAll(listOf("key")) { _, value -> value }

        assertEquals(result, listOf("value"))
        assertTrue(accessTrackingKeyValueStore.accessedEntries.isEmpty())
    }

    @Test
    fun entriesAreCachedAfterGettingMultipleEntriesAsMap() {
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

        assertEquals(result, mapOf(regularKey to nodeForRegularKey, prefetchKey to nodeForPrefetchKey))
        assertTrue(accessTrackingKeyValueStore.accessedEntries.isEmpty())
    }
}
