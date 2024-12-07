package org.modelix.model.server.store

import org.modelix.model.IGenericKeyListener
import org.slf4j.LoggerFactory

fun generateId(idStr: String?): Long {
    return try {
        val candidate = idStr?.toLong()
        if (candidate == null || candidate == Long.MAX_VALUE || candidate < 0L) {
            0L
        } else {
            candidate
        }
    } catch (e: NumberFormatException) {
        0L
    } + 1L
}

class InMemoryStoreClient : IsolatingStore {
    companion object {
        private val LOG = LoggerFactory.getLogger(InMemoryStoreClient::class.java)
    }

    private val values: MutableMap<ObjectInRepository, String?> = HashMap()
    private var transactionValues: MutableMap<ObjectInRepository, String?>? = null
    private val changeNotifier = ChangeNotifier(this)
    private val pendingChangeMessages = PendingChangeMessages(changeNotifier::notifyListeners)

    @Synchronized
    override fun get(key: ObjectInRepository): String? {
        return if (transactionValues?.contains(key) == true) transactionValues!![key] else values[key]
    }

    override fun getIfCached(key: ObjectInRepository): String? {
        return get(key)
    }

    @Synchronized
    override fun getAll(keys: List<ObjectInRepository>): List<String?> {
        return keys.map { get(it) }
    }

    @Synchronized
    override fun getAll(): Map<ObjectInRepository, String?> {
        return values + (transactionValues ?: emptyMap())
    }

    @Synchronized
    override fun getAll(keys: Set<ObjectInRepository>): Map<ObjectInRepository, String?> {
        return keys.associateWith { get(it) }
    }

    @Synchronized
    override fun put(key: ObjectInRepository, value: String?, silent: Boolean) {
        runTransaction {
            (transactionValues ?: values)[key] = value
            if (!silent) {
                pendingChangeMessages.entryChanged(key)
            }
        }
    }

    @Synchronized
    override fun putAll(entries: Map<ObjectInRepository, String?>, silent: Boolean) {
        runTransaction {
            for ((key, value) in entries) {
                put(key, value, silent)
            }
        }
    }

    @Synchronized
    override fun listen(key: ObjectInRepository, listener: IGenericKeyListener<ObjectInRepository>) {
        changeNotifier.addListener(key, listener)
    }

    @Synchronized
    override fun removeListener(key: ObjectInRepository, listener: IGenericKeyListener<ObjectInRepository>) {
        changeNotifier.removeListener(key, listener)
    }

    @Synchronized
    override fun generateId(key: ObjectInRepository): Long {
        val id = generateId(get(key))
        put(key, id.toString(), false)
        return id
    }

    @Synchronized
    override fun <T> runTransaction(body: () -> T): T {
        if (transactionValues == null) {
            return pendingChangeMessages.runAndFlush {
                try {
                    transactionValues = HashMap()
                    val result = body()
                    val tValues = requireNotNull(transactionValues) { "Passed lambda set 'transactionValues' to null unexpectedly." }

                    val puts = tValues.filterValues { it != null }
                    val deletes = tValues.filterValues { it == null }.keys
                    values.putAll(puts)
                    for (keyToDelete in deletes) {
                        values.remove(keyToDelete)
                    }
                    result
                } finally {
                    transactionValues = null
                }
            }
        } else {
            return body()
        }
    }

    override fun close() {
    }
}
