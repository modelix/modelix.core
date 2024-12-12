package org.modelix.model.server.store

import org.modelix.model.IGenericKeyListener

/**
 * Stores immutable objects where the key is the SHA hash of the value, which means the entries never change.
 * Doesn't require transactions.
 */
interface IImmutableStore<KeyT> {
    fun getAll(keys: Set<KeyT>): Map<KeyT, String>
    fun addAll(entries: Map<KeyT, String>)
    fun getIfCached(key: KeyT): String?
}

fun <KeyT> IImmutableStore<KeyT>.asGenericStore() = ImmutableStoreAsGenericStore(this)

class ImmutableStoreAsGenericStore<KeyT>(val store: IImmutableStore<KeyT>) : IGenericStoreClient<KeyT> {
    @RequiresTransaction
    override fun getAll(keys: Set<KeyT>): Map<KeyT, String?> {
        return store.getAll(keys)
    }

    @RequiresTransaction
    override fun getAll(): Map<KeyT, String?> {
        throw UnsupportedOperationException()
    }

    @RequiresTransaction
    override fun getIfCached(key: KeyT): String? {
        return store.getIfCached(key)
    }

    @RequiresTransaction
    override fun putAll(entries: Map<KeyT, String?>, silent: Boolean) {
        store.addAll(entries.mapValues { requireNotNull(it.value) { "Deleting entries not allowed: $it" } })
    }

    override fun listen(key: KeyT, listener: IGenericKeyListener<KeyT>) {
        throw UnsupportedOperationException()
    }

    override fun removeListener(key: KeyT, listener: IGenericKeyListener<KeyT>) {
        throw UnsupportedOperationException()
    }

    override fun generateId(key: KeyT): Long {
        throw UnsupportedOperationException()
    }

    override fun getTransactionManager(): ITransactionManager {
        return NoTransactionManager()
    }

    override fun getImmutableStore(): IImmutableStore<KeyT> {
        return store
    }

    override fun close() {
        throw UnsupportedOperationException()
    }
}
