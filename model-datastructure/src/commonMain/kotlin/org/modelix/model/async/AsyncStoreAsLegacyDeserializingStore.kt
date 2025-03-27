package org.modelix.model.async

import org.modelix.datastructures.objects.IObjectData
import org.modelix.datastructures.objects.IObjectDeserializer
import org.modelix.datastructures.objects.IObjectReferenceFactory
import org.modelix.model.IKeyValueStore
import org.modelix.model.lazy.IDeserializingKeyValueStore
import org.modelix.streams.IStreamExecutorProvider

private val ILLEGAL_DESERIALIZER: IObjectDeserializer<*> = object : IObjectDeserializer<IObjectData> {
    override fun deserialize(
        serialized: String,
        referenceFactory: IObjectReferenceFactory,
    ): IObjectData {
        error("deserialization not expected")
    }
}

class AsyncStoreAsLegacyDeserializingStore(val store: IAsyncObjectStore) : IDeserializingKeyValueStore, IStreamExecutorProvider by store {

    override fun getAsyncStore(): IAsyncObjectStore {
        return store
    }

    override fun <T : IObjectData> get(hash: String, deserializer: IObjectDeserializer<T>): T? {
        val ref = ObjectRequest(hash, deserializer, store.asObjectGraph())
        return getStreamExecutor().query { store.get(ref).orNull() } as T?
    }

    override val keyValueStore: IKeyValueStore
        get() = store.getLegacyKeyValueStore()

    override fun <T : IObjectData> getIfCached(hash: String, deserializer: IObjectDeserializer<T>, isPrefetch: Boolean): T? {
        return store.getIfCached(ObjectRequest(hash, deserializer, store.asObjectGraph())) as T?
    }

    override fun put(hash: String, deserialized: IObjectData, serialized: String) {
        getStreamExecutor().query {
            store.putAll(mapOf(ObjectRequest(hash, ILLEGAL_DESERIALIZER, store.asObjectGraph()) to deserialized as IObjectData)).andThenUnit()
        }
    }

    override fun <T : IObjectData> getAll(
        regular: List<ObjectRequest<T>>,
    ): Map<String, T?> {
        return getStreamExecutor().query { store.getAllAsMap(regular) }.entries.associate { it.key.hash to it.value as T? }
    }
}
