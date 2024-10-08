/*
 * Copyright (c) 2024.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.modelix.model.server.store

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
import java.lang.ref.SoftReference

class StoreManager(val genericStore: IsolatingStore) {

    private val repositorySpecificStores = HashMap<RepositoryId?, SoftReference<IAsyncObjectStore>>()
    val clientId: Int by lazy { getGlobalStoreClient().generateId("clientId").toInt() }
    val idGenerator: IIdGenerator by lazy { IdGenerator.getInstance(clientId) }

    fun getGlobalStoreClient() = getStoreClient(null)

    fun getStoreClient(repository: RepositoryId?): IStoreClient {
        return if (repository == null) {
            genericStore.forGlobalRepository()
        } else {
            genericStore.forRepository(repository)
        }
    }

    @Synchronized
    fun getAsyncStore(repository: RepositoryId?): IAsyncObjectStore {
        val existing = repositorySpecificStores[repository]?.get()
        if (existing != null) return existing

        val newStore = BulkAsyncStore(
            CachingAsyncStore(
                StoreClientAsAsyncStore(getStoreClient(repository)),
                cacheSize = System.getenv("MODELIX_OBJECT_CACHE_SIZE")?.toIntOrNull() ?: 500_000,
            ),
        )
        repositorySpecificStores[repository] = SoftReference(newStore)
        return newStore
    }

    fun getLegacyObjectStore(repository: RepositoryId?) = AsyncStoreAsLegacyDeserializingStore(getAsyncStore(repository))

    fun getGlobalKeyValueStore() = getKeyValueStore(null)

    fun getKeyValueStore(repository: RepositoryId?): IKeyValueStore {
        return StoreClientAsKeyValueStore(getStoreClient(repository))
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
