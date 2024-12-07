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
