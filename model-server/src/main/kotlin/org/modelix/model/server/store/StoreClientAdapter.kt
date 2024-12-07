package org.modelix.model.server.store

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

    override fun getIfCached(key: String): String? {
        val fromRepository = client.getIfCached(key.withRepoScope())
        if (fromRepository != null) return fromRepository
        // Existing databases may have objects stored without information about the repository.
        // Try to load these legacy entries.
        return client.getIfCached(ObjectInRepository.global(key))
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

fun IsolatingStore.forRepository(repository: RepositoryId): RepositoryScopedStoreClient {
    return RepositoryScopedStoreClient(repository, this)
}

fun IsolatingStore.forGlobalRepository() = GlobalStoreClient(this)

fun IStoreClient.getGenericStore(): IsolatingStore {
    return (this as StoreClientAdapter).client
}

data class RepositoryScopedKeyListener(val listener: IGenericKeyListener<String>) : IGenericKeyListener<ObjectInRepository> {
    override fun changed(key: ObjectInRepository, value: String?) {
        listener.changed(key.key, value)
    }
}
