package org.modelix.model.lazy

import org.modelix.model.IKeyValueStore
import org.modelix.model.async.ObjectRequest
import org.modelix.model.objects.IObjectData
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

    override fun <T : IObjectData> getAll(
        regular: List<ObjectRequest<T>>,
    ): Map<String, T?> {
        val allRequests = regular.asSequence()
        val hashes = allRequests.map { it.hash }
        val deserializers = allRequests.associate { it.hash to it.deserializer }
        val serialized: Map<String, String?> = keyValueStore.getAll(hashes.map { it.toString() }.asIterable())
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
