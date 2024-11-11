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

import org.modelix.model.IGenericKeyListener
import org.modelix.model.lazy.RepositoryId

/**
 * A store that saves data on a per-repository basis.
 * The primary key is of type [ObjectInRepository].
 */
interface IsolatingStore : IGenericStoreClient<ObjectInRepository> {
    /**
     * Default implementation for removing repository objects.
     * May be overridden by more efficient, store-specific implementations.
     *
     * Callers need to ensure that the repository is not usable anymore before calling this method.
     */
    fun removeRepositoryObjects(repositoryId: RepositoryId) {
        val keysToDelete = getAll().asSequence()
            .map { it.key }
            .filter { it.getRepositoryId() == repositoryId.id }
            .toSet()
        putAll(keysToDelete.associateWith { null })
    }
}

interface IGenericStoreClient<KeyT> : AutoCloseable {
    operator fun get(key: KeyT): String? = getAll(listOf(key)).first()
    fun getAll(keys: List<KeyT>): List<String?> {
        val entries = getAll(keys.toSet())
        return keys.map { entries[it] }
    }
    fun getIfCached(key: KeyT): String?
    fun getAll(keys: Set<KeyT>): Map<KeyT, String?>
    fun getAll(): Map<KeyT, String?>
    fun put(key: KeyT, value: String?, silent: Boolean = false) = putAll(mapOf(key to value))
    fun putAll(entries: Map<KeyT, String?>, silent: Boolean = false)
    fun listen(key: KeyT, listener: IGenericKeyListener<KeyT>)
    fun removeListener(key: KeyT, listener: IGenericKeyListener<KeyT>)
    fun generateId(key: KeyT): Long
    fun <T> runTransaction(body: () -> T): T
}
