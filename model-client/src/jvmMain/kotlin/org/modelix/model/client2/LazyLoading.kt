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
import org.modelix.model.lazy.ObjectStoreCache
import org.modelix.model.lazy.RepositoryId

fun IModelClientV2.lazyLoadVersion(repositoryId: RepositoryId, versionHash: String, cacheSize: Int = 100_000): IVersion {
    val store = ObjectStoreCache(ModelClientAsStore(this, repositoryId), cacheSize)
    return CLVersion.loadFromHash(versionHash, store)
}

suspend fun IModelClientV2.lazyLoadVersion(branchRef: BranchReference, cacheSize: Int = 100_000): IVersion {
    return lazyLoadVersion(branchRef.repositoryId, pullHash(branchRef), cacheSize)
}

class ModelClientAsStore(val client: IModelClientV2, val repositoryId: RepositoryId) : IKeyValueStore {
    override fun get(key: String): String? {
        return getAll(listOf(key))[key]
    }

    override fun put(key: String, value: String?) {
        TODO("Not yet implemented")
    }

    override fun getAll(keys: Iterable<String>): Map<String, String?> {
        return runBlocking {
            client.getObjects(repositoryId, keys.asSequence())
        }
    }

    override fun putAll(entries: Map<String, String?>) {
        TODO("Not yet implemented")
    }

    override fun prefetch(key: String) {
        TODO("Not yet implemented")
    }

    override fun listen(key: String, listener: IKeyListener) {
        TODO("Not yet implemented")
    }

    override fun removeListener(key: String, listener: IKeyListener) {
        TODO("Not yet implemented")
    }

    override fun getPendingSize(): Int {
        TODO("Not yet implemented")
    }
}
