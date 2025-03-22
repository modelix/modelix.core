package org.modelix.model.lazy

import org.modelix.model.IKeyListener
import org.modelix.model.IKeyValueStore
import org.modelix.streams.IStreamExecutorProvider

/**
 * Internal API.
 * Only public for tests.
 */
class AccessTrackingStore(val store: IKeyValueStore) : IKeyValueStore, IStreamExecutorProvider by store {
    val accessedEntries: MutableMap<String, String?> = LinkedHashMap()

    override fun get(key: String): String? {
        val value = store.get(key)
        accessedEntries.put(key, value)
        return value
    }

    override fun getIfCached(key: String): String? {
        val value = store.getIfCached(key)
        if (value != null) {
            accessedEntries[key] = value
        }
        return value
    }

    override fun put(key: String, value: String?) {
        TODO("Not yet implemented")
    }

    override fun getAll(keys: Iterable<String>): Map<String, String?> {
        val entries = store.getAll(keys)
        accessedEntries.putAll(entries)
        return entries
    }

    override fun putAll(entries: Map<String, String?>) {
        TODO("Not yet implemented")
    }

    override fun prefetch(key: String) {
        TODO("Not yet implemented")
    }

    override fun listen(key: String, listener: IKeyListener) {
        TODO("Not yet implemented")
    }

    override fun removeListener(key: String, listener: IKeyListener) {
        TODO("Not yet implemented")
    }

    override fun getPendingSize(): Int {
        TODO("Not yet implemented")
    }
}
