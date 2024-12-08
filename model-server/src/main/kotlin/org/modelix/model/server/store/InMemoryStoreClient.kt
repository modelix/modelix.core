/*
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *  http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
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
    private val locks = TransactionLocks()

    @Synchronized
    override fun get(key: ObjectInRepository): String? {
        locks.assertRead()
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
        locks.assertRead()
        return values + (transactionValues ?: emptyMap())
    }

    @Synchronized
    override fun getAll(keys: Set<ObjectInRepository>): Map<ObjectInRepository, String?> {
        return keys.associateWith { get(it) }
    }

    @Synchronized
    override fun put(key: ObjectInRepository, value: String?, silent: Boolean) {
        locks.assertWrite()
        (transactionValues ?: values)[key] = value
        if (!silent) {
            pendingChangeMessages.entryChanged(key)
        }
    }

    @Synchronized
    override fun putAll(entries: Map<ObjectInRepository, String?>, silent: Boolean) {
        for ((key, value) in entries) {
            put(key, value, silent)
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
        // This is an atomic operation that doesn't require the caller to start a transaction
        return runWriteTransaction {
            val id = generateId(get(key))
            put(key, id.toString(), false)
            id
        }
    }

    @Synchronized
    override fun <T> runWriteTransaction(body: () -> T): T {
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

    override fun <T> runReadTransaction(body: () -> T): T = locks.runRead(body)

    override fun close() {
    }
}
