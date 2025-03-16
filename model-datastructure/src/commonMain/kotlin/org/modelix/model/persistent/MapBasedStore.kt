package org.modelix.model.persistent

import org.modelix.kotlin.utils.createMemoryEfficientMap
import org.modelix.kotlin.utils.toSynchronizedMap
import org.modelix.model.IKeyListener
import org.modelix.model.IKeyValueStore
import org.modelix.streams.IStreamExecutor
import org.modelix.streams.SimpleStreamExecutor
import org.modelix.streams.withSequences

@Deprecated("Use MapBasedStore, without a typo.", ReplaceWith("MapBasedStore"))
open class MapBaseStore : MapBasedStore()

open class MapBasedStore : IKeyValueStore {
    private val map = createMemoryEfficientMap<String?, String?>().toSynchronizedMap()

    override fun getStreamExecutor(): IStreamExecutor = SimpleStreamExecutor().withSequences()

    override fun get(key: String): String? {
        return map[key]
    }

    override fun getIfCached(key: String): String? {
        return get(key)
    }

    override fun getPendingSize(): Int = 0

    override fun put(key: String, value: String?) {
        putAll(mapOf(key to value))
    }

    override fun getAll(keys: Iterable<String>): Map<String, String?> {
        val result: MutableMap<String, String?> = LinkedHashMap()
        for (key in keys) {
            result[key] = map[key]
        }
        return result
    }

    override fun putAll(entries: Map<String, String?>) {
        map.putAll(entries)
    }

    override fun prefetch(key: String) {}
    val entries: Iterable<Map.Entry<String?, String?>>
        get() = map.entries

    override fun listen(key: String, listener: IKeyListener) {
        throw UnsupportedOperationException()
    }

    override fun removeListener(key: String, listener: IKeyListener) {
        throw UnsupportedOperationException()
    }
}
