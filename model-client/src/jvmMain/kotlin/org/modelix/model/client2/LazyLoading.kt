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

package org.modelix.model.client2

import kotlinx.coroutines.runBlocking
import org.modelix.model.IKeyListener
import org.modelix.model.IKeyValueStore
import org.modelix.model.IVersion
import org.modelix.model.lazy.BranchReference
import org.modelix.model.lazy.CLVersion
import org.modelix.model.lazy.CacheConfiguration
import org.modelix.model.lazy.ObjectStoreCache
import org.modelix.model.lazy.RepositoryId
import org.modelix.model.persistent.HashUtil

/**
 * This function loads parts of the model lazily while it is iterated and limits the amount of data that is cached on
 * the client side.
 *
 * IModelClientV2#loadVersion eagerly loads the whole model. For large models this can be slow and requires lots of
 * memory.
 * To reduce the relative overhead of requests to the server, the lazy loading algorithm tries to predict which nodes
 * are required next and fill a "prefetch cache" by using "free capacity" of the regular requests. That means,
 * the number of requests doesn't change by this prefetching, but small requests are filled to up to their limit with
 * additional prefetch requests.
 */
fun IModelClientV2.lazyLoadVersion(repositoryId: RepositoryId, versionHash: String, config: CacheConfiguration = CacheConfiguration()): IVersion {
    val store = ObjectStoreCache(ModelClientAsStore(this, repositoryId), config)
    return CLVersion.loadFromHash(versionHash, store)
}

/**
 * An overload of [IModelClientV2.lazyLoadVersion] that reads the current version hash of the branch from the server and
 * then loads that version with lazy loading support.
 */
suspend fun IModelClientV2.lazyLoadVersion(branchRef: BranchReference, config: CacheConfiguration = CacheConfiguration()): IVersion {
    return lazyLoadVersion(branchRef.repositoryId, pullHash(branchRef), config)
}

private class ModelClientAsStore(client: IModelClientV2, val repositoryId: RepositoryId) : IKeyValueStore {
    private val client: IModelClientV2Internal = client as IModelClientV2Internal

    override fun get(key: String): String? {
        return getAll(listOf(key))[key]
    }

    override fun getIfCached(key: String): String? {
        return null
    }

    override fun put(key: String, value: String?) {
        putAll(mapOf(key to value))
    }

    override fun getAll(keys: Iterable<String>): Map<String, String?> {
        return runBlocking {
            client.getObjects(repositoryId, keys.asSequence())
        }
    }

    override fun putAll(entries: Map<String, String?>) {
        runBlocking {
            client.pushObjects(
                repositoryId,
                entries.asSequence().map { (key, value) ->
                    require(HashUtil.isSha256(key) && value != null) { "Only immutable objects are allowed: $key -> $value" }
                    key to value
                },
            )
        }
    }

    override fun prefetch(key: String) {
        throw UnsupportedOperationException()
    }

    override fun listen(key: String, listener: IKeyListener) {
        throw UnsupportedOperationException()
    }

    override fun removeListener(key: String, listener: IKeyListener) {
        throw UnsupportedOperationException()
    }

    override fun getPendingSize(): Int {
        return 0
    }
}
