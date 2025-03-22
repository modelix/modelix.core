package org.modelix.model.client2

import kotlinx.coroutines.flow.flow
import org.modelix.model.IKeyValueStore
import org.modelix.model.async.AsyncStoreAsLegacyDeserializingStore
import org.modelix.model.async.IAsyncObjectStore
import org.modelix.model.async.ObjectRequest
import org.modelix.model.lazy.IDeserializingKeyValueStore
import org.modelix.model.lazy.RepositoryId
import org.modelix.model.objects.IObjectData
import org.modelix.model.persistent.HashUtil
import org.modelix.streams.IStream
import org.modelix.streams.IStreamExecutor
import org.modelix.streams.SimpleStreamExecutor
import org.modelix.streams.withFlows

class ModelClientAsStore(client: IModelClientV2, val repositoryId: RepositoryId) : IAsyncObjectStore {
    private val client: IModelClientV2Internal = client as IModelClientV2Internal

    override fun getLegacyKeyValueStore(): IKeyValueStore {
        throw UnsupportedOperationException()
    }

    override fun getLegacyObjectStore(): IDeserializingKeyValueStore {
        return AsyncStoreAsLegacyDeserializingStore(this)
    }

    override fun clearCache() {}

    override fun <T : IObjectData> getIfCached(key: ObjectRequest<T>): T? {
        return null
    }

    override fun <T : IObjectData> get(key: ObjectRequest<T>): IStream.ZeroOrOne<T> {
        return getAllAsStream(IStream.of(key)).map {
            checkNotNull(it.second) { "Entry not found: ${key.hash}" } as T
        }.exactlyOne()
    }

    override fun getAllAsStream(keys: IStream.Many<ObjectRequest<*>>): IStream.Many<Pair<ObjectRequest<*>, IObjectData?>> {
        return keys.toList().flatMap { keysAsList ->
            getAllAsMap(keysAsList).flatMapIterable { it.entries }.map { it.key to it.value }
        }
    }

    override fun getAllAsMap(keys: List<ObjectRequest<*>>): IStream.One<Map<ObjectRequest<*>, IObjectData?>> {
        return IStream.fromFlow(
            flow {
                val serializedObjects = client.getObjects(repositoryId, keys.asSequence().map { it.hash })
                val deserializedObjects = keys.associateWith {
                    serializedObjects[it.hash]?.let { p1 -> it.deserializer.deserialize(p1, it.referenceFactory) }
                }
                emit(deserializedObjects)
            },
        ).exactlyOne()
    }

    override fun putAll(entries: Map<ObjectRequest<*>, IObjectData>): IStream.Zero {
        return IStream.fromFlow<Nothing>(
            flow {
                client.pushObjects(
                    repositoryId,
                    entries.asSequence().map { (key, value) ->
                        require(HashUtil.isSha256(key.hash)) { "Only immutable objects are allowed: $key -> $value" }
                        key.hash to value.serialize()
                    },
                )
            },
        ).drainAll()
    }

    override fun getStreamExecutor(): IStreamExecutor {
        return SimpleStreamExecutor().withFlows()
    }
}
