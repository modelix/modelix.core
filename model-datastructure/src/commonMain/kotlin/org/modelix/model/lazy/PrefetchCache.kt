package org.modelix.model.lazy

import org.modelix.kotlin.utils.ContextValue
import org.modelix.model.IKeyValueStore
import org.modelix.model.api.ITree
import org.modelix.model.persistent.IKVValue

/**
 * There is no size limit. Entries are not evicted.
 * This guarantees that after a prefetch there are no more request required.
 * Not thread safe.
 */
@Deprecated("BulkQuery is now responsible for prefetching")
class PrefetchCache(private val store: IDeserializingKeyValueStore) : IDeserializingKeyValueStore {
    init {
        if (store is ContextIndirectCache) throw IllegalArgumentException()
        if (store is PrefetchCache) throw IllegalArgumentException()
    }

    private val entries: MutableMap<String, Any?> = HashMap()

    override val keyValueStore: IKeyValueStore = store.keyValueStore

    override fun <T> get(hash: String, deserializer: (String) -> T): T? {
        return get(hash, deserializer, false, false)
    }

    private fun <T> get(hash: String, deserializer: (String) -> T, ifCached: Boolean, isPrefetch: Boolean): T? {
        return if (entries.containsKey(hash)) {
            entries[hash] as T?
        } else {
            val value = if (ifCached) store.getIfCached(hash, deserializer, isPrefetch) else store.get(hash, deserializer)
            if (value != null) {
                entries[hash] = value
            }
            value
        }
    }

    override fun <T> getIfCached(hash: String, deserializer: (String) -> T, isPrefetch: Boolean): T? {
        return get(hash, deserializer, true, isPrefetch)
    }

    override fun <T> getAll(hashes: Iterable<String>, deserializer: (String, String) -> T): Iterable<T> {
        val missingHashes = hashes.filterNot { entries.containsKey(it) }
        val missingValues = store.getAll(missingHashes, deserializer).toList()
        val missingEntries = missingHashes.mapIndexed { index, hash -> hash to missingValues[index] }.associate { it }
        entries.putAll(missingEntries)
        return hashes.map { entries[it] as T }
    }

    override fun <T : IKVValue> getAll(regular: List<IKVEntryReference<T>>, prefetch: List<IKVEntryReference<T>>): Map<String, T?> {
        val missingRegular = regular.filterNot { entries.containsKey(it.getHash()) }
        val missingPrefetch = prefetch.filterNot { entries.containsKey(it.getHash()) }
        val missingEntries = store.getAll(missingRegular, missingPrefetch)
        for ((key, value) in missingEntries) {
            if (value != null) {
                entries[key] = value
            }
        }
        val regularAndPrefetch = regular.asSequence() + prefetch.asSequence()
        return regularAndPrefetch.associate { it.getHash() to entries[it.getHash()] as T? }
    }

    override fun put(hash: String, deserialized: Any, serialized: String) {
        entries[hash] = deserialized
        store.put(hash, deserialized, serialized)
    }

    override fun prefetch(hash: String) {
        store.prefetch(hash)
    }

    companion object {
        private val contextValue: ContextValue<PrefetchCache> = ContextValue()

        fun <T> with(store_: IDeserializingKeyValueStore, f: () -> T): T {
            val store = if (store_ is ContextIndirectCache) store_.directStore else store_
            val unwrapped = unwrap(store)
            val current = contextValue.getValueOrNull()
            return if (current != null && current.store == unwrapped) {
                f()
            } else {
                if (store is PrefetchCache) {
                    contextValue.computeWith(store, f)
                } else {
                    contextValue.computeWith(PrefetchCache(unwrapped), f)
                }
            }
        }

        fun <T> with(tree: ITree, f: () -> T): T {
            return with((tree.unwrap() as CLTree).store, f)
        }

        fun unwrap(store: IDeserializingKeyValueStore): IDeserializingKeyValueStore {
            return when (store) {
                is PrefetchCache -> unwrap(store.store)
                is ContextIndirectCache -> store.directStore
                else -> store
            }
        }

        fun contextIndirectCache(store: IDeserializingKeyValueStore): IDeserializingKeyValueStore {
            return ContextIndirectCache(store)
        }

        class ContextIndirectCache(val directStore: IDeserializingKeyValueStore) : IndirectObjectStore() {
            init {
                if (directStore is ContextIndirectCache) throw IllegalArgumentException()
                if (directStore is PrefetchCache) throw IllegalArgumentException()
            }

            override fun getStore(): IDeserializingKeyValueStore {
                return contextValue.getValueOrNull() ?: directStore
            }
        }
    }
}
