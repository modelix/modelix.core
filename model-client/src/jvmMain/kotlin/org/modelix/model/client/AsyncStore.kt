package org.modelix.model.client

import org.modelix.model.IKeyListener
import org.modelix.model.IKeyValueStore
import org.modelix.model.IKeyValueStoreWrapper
import org.modelix.streams.IStreamExecutorProvider
import java.util.Objects
import java.util.concurrent.atomic.AtomicBoolean

class AsyncStore(private val store: IKeyValueStore) : IKeyValueStoreWrapper, IStreamExecutorProvider by store {
    private val consumerActive = AtomicBoolean()
    private val pendingWrites: MutableMap<String, String?> = LinkedHashMap()
    override fun get(key: String): String? {
        synchronized(pendingWrites) {
            if (pendingWrites.containsKey(key)) {
                return pendingWrites.get(key)
            }
        }
        return store[key]
    }

    override fun getIfCached(key: String): String? {
        return synchronized(pendingWrites) {
            pendingWrites[key]
        } ?: store.getIfCached(key)
    }

    override fun getWrapped(): IKeyValueStore = store

    override fun getPendingSize(): Int = store.getPendingSize() + pendingWrites.size

    override fun listen(key: String, listener: IKeyListener) {
        store.listen(key, listener)
    }

    override fun removeListener(key: String, listener: IKeyListener) {
        store.removeListener(key, listener)
    }

    override fun put(key: String, value: String?) {
        putAll(mapOf(key to value))
    }

    override fun getAll(keys: Iterable<String>): Map<String, String?> {
        val mutableKeys = keys.toMutableList()
        val result: MutableMap<String, String?> = LinkedHashMap()
        synchronized(pendingWrites) {
            val itr: MutableIterator<String> = mutableKeys.iterator()
            while (itr.hasNext()) {
                val key: String = itr.next()
                // always put even if null to have the same order in the linked hash map as in the input
                result.put(key, pendingWrites.get(key))
                if (pendingWrites.containsKey(key)) {
                    itr.remove()
                }
            }
        }
        if (mutableKeys.isNotEmpty()) {
            result.putAll(store.getAll(mutableKeys))
        }
        return result
    }

    override fun putAll(entries: Map<String, String?>) {
        synchronized(pendingWrites) {
            // ensure correct order
            for (newEntry in entries) {
                val existingValue = pendingWrites[newEntry.key]
                if (existingValue != newEntry.value) {
                    pendingWrites.remove(newEntry.key)
                }
            }

            pendingWrites.putAll(entries)
        }
        processQueue()
    }

    override fun prefetch(key: String) {
        store.prefetch(key)
    }

    protected fun processQueue() {
        if (consumerActive.compareAndSet(false, true)) {
            SharedExecutors.FIXED.execute(
                Runnable {
                    try {
                        while (!pendingWrites.isEmpty()) {
                            try {
                                val entries: MutableMap<String, String?> = LinkedHashMap(16, 0.75.toFloat(), false)
                                synchronized(pendingWrites) { entries.putAll(pendingWrites) }
                                store.putAll(entries)
                                synchronized(pendingWrites) {
                                    for (entry: Map.Entry<String?, String?> in entries.entries) {
                                        if (Objects.equals(pendingWrites.get(entry.key), entry.value)) {
                                            pendingWrites.remove(entry.key)
                                        }
                                    }
                                }
                            } catch (ex: Exception) {
                                LOG.error(ex) { "" }
                                try {
                                    Thread.sleep(1000)
                                } catch (ex2: InterruptedException) {
                                    return@Runnable
                                }
                            }
                        }
                    } finally {
                        consumerActive.set(false)
                    }
                },
            )
        }
    }

    fun dispose() {}

    companion object {
        private val LOG = mu.KotlinLogging.logger {}
    }
}
