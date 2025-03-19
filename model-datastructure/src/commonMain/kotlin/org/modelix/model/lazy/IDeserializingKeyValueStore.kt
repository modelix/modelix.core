package org.modelix.model.lazy

import org.modelix.model.IKeyValueStore
import org.modelix.model.async.IAsyncObjectStore
import org.modelix.model.async.ObjectRequest
import org.modelix.model.objects.IObjectData
import org.modelix.model.objects.IObjectDeserializer
import org.modelix.streams.IStreamExecutorProvider

interface IDeserializingKeyValueStore : IStreamExecutorProvider {
    val keyValueStore: IKeyValueStore
    operator fun <T : IObjectData> get(hash: String, deserializer: IObjectDeserializer<T>): T?
    fun <T : IObjectData> getIfCached(hash: String, deserializer: IObjectDeserializer<T>, isPrefetch: Boolean): T?
    fun <T : IObjectData> getAll(regular: List<ObjectRequest<T>>): Map<String, T?> = throw UnsupportedOperationException()
    fun put(hash: String, deserialized: IObjectData, serialized: String)
    fun getAsyncStore(): IAsyncObjectStore
}
