package org.modelix.model.server.store

import org.modelix.model.IGenericKeyListener
import org.modelix.model.persistent.HashUtil
import org.modelix.model.server.handlers.ObjectValueNotFoundException
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

class InMemoryStoreClient : IsolatingStore, ITransactionManager, IRepositoryAwareStore {
    companion object {
        private val LOG = LoggerFactory.getLogger(InMemoryStoreClient::class.java)
    }

    private val values: MutableMap<ObjectInRepository, String?> = HashMap()
    private var transactionValues: MutableMap<ObjectInRepository, String?>? = null
    private val changeNotifier = ChangeNotifier(this)
    private val pendingChangeMessages = PendingChangeMessages(changeNotifier::notifyListeners)
    private val locks = TransactionLocks()

    override fun get(key: ObjectInRepository): String? {
        locks.assertRead()
        return if (transactionValues?.contains(key) == true) transactionValues!![key] else values[key]
    }

    override fun getIfCached(key: ObjectInRepository): String? {
        locks.assertRead()
        return get(key)
    }

    override fun getAll(keys: List<ObjectInRepository>): List<String?> {
        locks.assertRead()
        return keys.map { get(it) }
    }

    override fun getAll(): Map<ObjectInRepository, String?> {
        locks.assertRead()
        return values + (transactionValues ?: emptyMap())
    }

    override fun getAll(keys: Set<ObjectInRepository>): Map<ObjectInRepository, String?> {
        locks.assertRead()
        return keys.associateWith { get(it) }
    }

    override fun put(key: ObjectInRepository, value: String?, silent: Boolean) {
        locks.assertWrite()
        (transactionValues ?: values)[key] = value
        if (!silent) {
            pendingChangeMessages.entryChanged(key)
        }
    }

    override fun putAll(entries: Map<ObjectInRepository, String?>, silent: Boolean) {
        locks.assertWrite()
        for ((key, value) in entries) {
            put(key, value, silent)
        }
    }

    override fun listen(key: ObjectInRepository, listener: IGenericKeyListener<ObjectInRepository>) {
        changeNotifier.addListener(key, listener)
    }

    override fun removeListener(key: ObjectInRepository, listener: IGenericKeyListener<ObjectInRepository>) {
        changeNotifier.removeListener(key, listener)
    }

    override fun generateId(key: ObjectInRepository): Long {
        // This is an atomic operation that doesn't require the caller to start a transaction
        return runWriteTransaction {
            val id = generateId(get(key))
            put(key, id.toString(), false)
            id
        }
    }

    override fun <T> runWrite(body: () -> T): T {
        return locks.runWrite {
            if (transactionValues == null) {
                pendingChangeMessages.runAndFlush {
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
                body()
            }
        }
    }

    override fun <T> runRead(body: () -> T): T {
        return locks.runRead(body)
    }

    override fun canRead(): Boolean {
        return locks.canRead()
    }

    override fun canWrite(): Boolean {
        return locks.canWrite()
    }

    override fun getTransactionManager(): ITransactionManager {
        return this
    }

    override fun getImmutableStore(): IImmutableStore<ObjectInRepository> {
        return object : IImmutableStore<ObjectInRepository> {
            override fun getAll(keys: Set<ObjectInRepository>): Map<ObjectInRepository, String> {
                keys.forEach { require(HashUtil.isSha256(it.key)) { "Not an immutable object: $it" } }
                return runRead { this@InMemoryStoreClient.getAll(keys) }.mapValues {
                    val value = it.value
                    if (value == null) throw ObjectValueNotFoundException(it.key.key)
                    value
                }
            }

            override fun addAll(entries: Map<ObjectInRepository, String>) {
                entries.forEach {
                    require(HashUtil.isSha256(it.key.key)) { "Not an immutable object: $it" }
                    HashUtil.checkObjectHash(it.key.key, it.value)
                }
                runWrite { this@InMemoryStoreClient.putAll(entries) }
            }

            override fun getIfCached(key: ObjectInRepository): String? {
                require(HashUtil.isSha256(key.key)) { "Not an immutable object: $key" }
                return runRead { this@InMemoryStoreClient.getIfCached(key) }
            }
        }
    }

    override fun close() {
    }
}
