import org.modelix.model.lazy.AccessTrackingStore
import org.modelix.model.lazy.NonCachingObjectStore
import org.modelix.model.lazy.PrefetchCache
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
}
