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

import org.modelix.kotlin.utils.ContextValue
import org.modelix.model.IGenericKeyListener
import org.modelix.model.lazy.RepositoryId
import org.modelix.model.persistent.HashUtil

abstract class StoreClientAdapter(val client: IsolatingStore) : IStoreClient {

    abstract fun getRepositoryId(): RepositoryId?

    private fun String.withRepoScope(): ObjectInRepository {
        return if (HashUtil.isSha256(this)) {
            val id = getRepositoryId()
            ObjectInRepository.create(id?.id, this)
        } else {
            ObjectInRepository.global(this)
        }
    }

    override fun get(key: String): String? {
        return getAll(setOf(key))[key]
    }

    override fun getAll(keys: List<String>): List<String?> {
        val map = getAll(keys.toSet())
        return keys.map { map[it] }
    }

    override fun getAll(keys: Set<String>): Map<String, String?> {
        val fromRepository = client.getAll(keys.map { it.withRepoScope() }.toSet()).mapKeys { it.key.key }
        if (getRepositoryId() == null) return fromRepository

        // Existing databases may have objects stored without information about the repository.
        // Try to load these legacy entries.
        val missingKeys = fromRepository.entries.asSequence().filter { it.value == null }.map {
            ObjectInRepository.global(
                it.key,
            )
        }.toSet()
        if (missingKeys.isEmpty()) return fromRepository
        val fromGlobal = client.getAll(missingKeys).mapKeys { it.key.key }

        return fromRepository + fromGlobal
    }

    override fun getAll(): Map<String, String?> {
        throw UnsupportedOperationException()
        // return client.getAll().filterKeys { it.repositoryId == null || it.repositoryId == repositoryId?.id }.mapKeys { it.key.key }
    }

    override fun put(key: String, value: String?, silent: Boolean) {
        client.put(key.withRepoScope(), value, silent)
    }

    override fun putAll(entries: Map<String, String?>, silent: Boolean) {
        client.putAll(entries.mapKeys { it.key.withRepoScope() }, silent)
    }

    override fun listen(key: String, listener: IGenericKeyListener<String>) {
        client.listen(key.withRepoScope(), RepositoryScopedKeyListener(listener))
    }

    override fun removeListener(key: String, listener: IGenericKeyListener<String>) {
        client.removeListener(key.withRepoScope(), RepositoryScopedKeyListener(listener))
    }

    override fun generateId(key: String): Long {
        return client.generateId(key.withRepoScope())
    }

    override fun <T> runTransaction(body: () -> T): T {
        return client.runTransaction(body)
    }

    override fun close() {
        client.close()
    }
}

class RepositoryScopedStoreClient(private val repositoryId: RepositoryId, client: IsolatingStore) : StoreClientAdapter(client) {
    override fun getRepositoryId() = repositoryId
}

class GlobalStoreClient(client: IsolatingStore) : StoreClientAdapter(client) {
    override fun getRepositoryId() = null
}

class ContextScopedStoreClient(client: IsolatingStore) : StoreClientAdapter(client) {
    companion object {
        val CONTEXT_REPOSITORY = ContextValue<RepositoryId?>()
    }

    override fun getRepositoryId() = CONTEXT_REPOSITORY.getValue()
}

fun IsolatingStore.forRepository(repository: RepositoryId): RepositoryScopedStoreClient {
    return RepositoryScopedStoreClient(repository, this)
}

fun IsolatingStore.forGlobalRepository() = GlobalStoreClient(this)

fun IsolatingStore.forContextRepository() = ContextScopedStoreClient(this)

fun <R> IStoreClient.withGlobalRepository(body: () -> R): R = withRepository(null, body)

fun <R> IStoreClient.withRepository(repository: RepositoryId?, body: () -> R): R {
    assert(this is ContextScopedStoreClient || this is StoreClientAdapter && this.getRepositoryId() == repository) {
        "Store is not context scoped: $this"
    }
    return ContextScopedStoreClient.CONTEXT_REPOSITORY.computeWith(repository, body)
}

suspend fun <R> IStoreClient.withGlobalRepositoryInCoroutine(body: suspend () -> R): R = withRepositoryInCoroutine(null, body)

suspend fun <R> IStoreClient.withRepositoryInCoroutine(repository: RepositoryId?, body: suspend () -> R): R {
    assert(this is ContextScopedStoreClient || this is StoreClientAdapter && this.getRepositoryId() == repository) {
        "Store is not context scoped: $this"
    }
    return ContextScopedStoreClient.CONTEXT_REPOSITORY.runInCoroutine(repository, body)
}

fun IStoreClient.forRepository(repository: RepositoryId): IStoreClient {
    return RepositoryScopedStoreClient(repository, getGenericStore())
}

fun IStoreClient.getGenericStore(): IsolatingStore {
    return (this as StoreClientAdapter).client
}

data class RepositoryScopedKeyListener(val listener: IGenericKeyListener<String>) : IGenericKeyListener<ObjectInRepository> {
    override fun changed(key: ObjectInRepository, value: String?) {
        listener.changed(key.key, value)
    }
}
