package org.modelix.model.lazy

import org.modelix.model.IKeyValueStore
import org.modelix.model.persistent.IKVValue
import org.modelix.streams.IStreamExecutorProvider

class NonCachingObjectStore(override val keyValueStore: IKeyValueStore) : IDeserializingKeyValueStore, IStreamExecutorProvider by keyValueStore {

    override fun <T> getAll(hashes_: Iterable<String>, deserializer: (String, String) -> T): Iterable<T> {
        val hashes = hashes_.toList()
        val serialized: Map<String, String?> = keyValueStore.getAll(hashes_)
        return hashes.map { hash ->
            val value = checkNotNull(serialized[hash]) { "Entry not found: $hash" }
            deserializer(hash, value)
        }
    }

    override fun <T : IKVValue> getAll(
        regular: List<IKVEntryReference<T>>,
    ): Map<String, T?> {
        val allRequests = regular.asSequence()
        val hashes = allRequests.map { it.getHash() }
        val deserializers = allRequests.associate { it.getHash() to it.getDeserializer() }
        val serialized: Map<String, String?> = keyValueStore.getAll(hashes.asIterable())
        return serialized.mapValues { (hash, serializedValue) ->
            val value = checkNotNull(serializedValue) { "Entry not found: $hash" }
            deserializers[hash]!!(value)
        }
    }

    override fun <T> get(hash: String, deserializer: (String) -> T): T? {
        return keyValueStore.get(hash)?.let(deserializer)
    }

    override fun <T> getIfCached(hash: String, deserializer: (String) -> T, isPrefetch: Boolean): T? {
        return keyValueStore.getIfCached(hash)?.let(deserializer)
    }

    override fun put(hash: String, deserialized: Any, serialized: String) {
        keyValueStore.put(hash, serialized)
    }
}
