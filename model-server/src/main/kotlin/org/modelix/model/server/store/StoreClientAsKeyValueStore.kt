package org.modelix.model.server.store

import org.apache.commons.collections4.IterableUtils
import org.modelix.model.IKeyListener
import org.modelix.model.IKeyValueStore

class StoreClientAsKeyValueStore(val store: IStoreClient) : IKeyValueStore {

    override fun get(key: String): String? {
        return store[key]
    }

    override fun getIfCached(key: String): String? {
        return null
    }

    override fun put(key: String, value: String?) {
        store.put(key, value)
    }

    override fun getAll(keys: Iterable<String>): Map<String, String?> {
        val keyList = IterableUtils.toList(keys)
        val values = store.getAll(keyList)
        val result: MutableMap<String, String?> = LinkedHashMap()
        for (i in keyList.indices) {
            result[keyList[i]] = values[i]
        }
        return result
    }

    override fun putAll(entries: Map<String, String?>) {
        store.putAll(entries)
    }

    override fun prefetch(key: String) {
        throw UnsupportedOperationException()
    }

    override fun listen(key: String, listener: IKeyListener) {
        store.listen(key, listener)
    }

    override fun removeListener(key: String, listener: IKeyListener) {
        store.removeListener(key, listener)
    }

    override fun getPendingSize(): Int {
        return 0
    }
}
