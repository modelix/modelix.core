package org.modelix.model.server.store

import com.google.common.cache.CacheBuilder
import org.modelix.model.IKeyValueStore
import org.modelix.model.api.IIdGenerator
import org.modelix.model.async.AsyncStoreAsLegacyDeserializingStore
import org.modelix.model.async.BulkAsyncStore
import org.modelix.model.async.CachingAsyncStore
import org.modelix.model.async.IAsyncObjectStore
import org.modelix.model.client.IModelClient
import org.modelix.model.client.IdGenerator
import org.modelix.model.lazy.IDeserializingKeyValueStore
import org.modelix.model.lazy.RepositoryId

class StoreManager(val genericStore: IRepositoryAwareStore) {
    private val repositorySpecificStores = CacheBuilder.newBuilder().softValues().build<String, IAsyncObjectStore>()
    val clientId: Int by lazy { getGlobalStoreClient().generateId("clientId").toInt() }
    val idGenerator: IIdGenerator by lazy { IdGenerator.getInstance(clientId) }

    fun getTransactionManager(): ITransactionManager = genericStore.getTransactionManager()

    fun getGlobalStoreClient(immutable: Boolean = false) = getStoreClient(null, immutable)

    fun getStoreClient(repository: RepositoryId?, immutable: Boolean): IStoreClient {
        return (if (immutable) genericStore.getImmutableStore().asGenericStore() else genericStore).let {
            if (repository == null) {
                it.forGlobalRepository()
            } else {
                it.forRepository(repository)
            }
        }
    }

    @Synchronized
    fun getAsyncStore(repository: RepositoryId?): IAsyncObjectStore {
        return repositorySpecificStores.get(repository?.id ?: "") {
            BulkAsyncStore(
                CachingAsyncStore(
                    StoreClientAsAsyncStore(getStoreClient(repository, true)),
                    cacheSize = System.getenv("MODELIX_OBJECT_CACHE_SIZE")?.toIntOrNull() ?: 500_000,
                ),
            )
        }
    }

    fun getLegacyObjectStore(repository: RepositoryId?) = AsyncStoreAsLegacyDeserializingStore(getAsyncStore(repository))

    fun getGlobalKeyValueStore() = getKeyValueStore(null)

    fun getKeyValueStore(repository: RepositoryId?): IKeyValueStore {
        return StoreClientAsKeyValueStore(getStoreClient(repository, true))
    }

    fun asModelClient(repository: RepositoryId?): IModelClient {
        return object : IModelClient, IKeyValueStore by getKeyValueStore(repository) {
            override val asyncStore: IKeyValueStore
                get() = this
            override val clientId: Int
                get() = this@StoreManager.clientId
            override val idGenerator: IIdGenerator
                get() = this@StoreManager.idGenerator
            override val storeCache: IDeserializingKeyValueStore
                get() = getLegacyObjectStore(repository)
        }
    }
}
