package org.modelix.model.client2

import kotlinx.coroutines.flow.flow
import org.modelix.model.IKeyValueStore
import org.modelix.model.async.AsyncStoreAsLegacyDeserializingStore
import org.modelix.model.async.IAsyncObjectStore
import org.modelix.model.async.ObjectHash
import org.modelix.model.lazy.IDeserializingKeyValueStore
import org.modelix.model.lazy.RepositoryId
import org.modelix.model.persistent.HashUtil
import org.modelix.model.persistent.IKVValue
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

    override fun <T : Any> getIfCached(key: ObjectHash<T>): T? {
        return null
    }

    override fun <T : Any> get(key: ObjectHash<T>): IStream.ZeroOrOne<T> {
        return getAllAsStream(IStream.of(key)).map {
            checkNotNull(it.second) { "Entry not found: ${key.hash}" } as T
        }.exactlyOne()
    }

    override fun getAllAsStream(keys: IStream.Many<ObjectHash<*>>): IStream.Many<Pair<ObjectHash<*>, Any?>> {
        return keys.toList().flatMap { keysAsList ->
            getAllAsMap(keysAsList).flatMapIterable { it.entries }.map { it.key to it.value }
        }
    }

    override fun getAllAsMap(keys: List<ObjectHash<*>>): IStream.One<Map<ObjectHash<*>, Any?>> {
        return IStream.fromFlow(
            flow {
                val serializedObjects = client.getObjects(repositoryId, keys.asSequence().map { it.hash })
                val deserializedObjects = keys.associateWith {
                    serializedObjects[it.hash]?.let { p1 -> it.deserializer(p1) }
                }
                emit(deserializedObjects)
            },
        ).exactlyOne()
    }

    override fun putAll(entries: Map<ObjectHash<*>, IKVValue>): IStream.Zero {
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
