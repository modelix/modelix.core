package org.modelix.model.lazy

import org.modelix.model.IKeyValueStore
import org.modelix.model.persistent.IKVValue

abstract class IndirectObjectStore : IDeserializingKeyValueStore {

    abstract fun getStore(): IDeserializingKeyValueStore

    override val keyValueStore: IKeyValueStore
        get() = getStore().keyValueStore

    override fun <T> get(hash: String, deserializer: (String) -> T): T? {
        return getStore().get(hash, deserializer)
    }

    override fun <T> getIfCached(hash: String, deserializer: (String) -> T, isPrefetch: Boolean): T? {
        return getStore().getIfCached(hash, deserializer, isPrefetch)
    }

    override fun <T> getAll(hash: Iterable<String>, deserializer: (String, String) -> T): Iterable<T> {
        return getStore().getAll(hash, deserializer)
    }

    override fun <T : IKVValue> getAll(regular: List<IKVEntryReference<T>>): Map<String, T?> {
        return getStore().getAll(regular)
    }

    override fun put(hash: String, deserialized: Any, serialized: String) {
        getStore().put(hash, deserialized, serialized)
    }
}
