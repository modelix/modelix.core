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

import org.modelix.model.IKeyListener
import org.slf4j.LoggerFactory
import kotlin.collections.HashMap

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

class InMemoryStoreClient : IStoreClient {
    companion object {
        private val LOG = LoggerFactory.getLogger(InMemoryStoreClient::class.java)
    }

    private val values: MutableMap<String, String?> = HashMap()
    private var transactionValues: MutableMap<String, String?>? = null
    private val changeNotifier = ChangeNotifier(this)
    private val pendingChangeMessages = PendingChangeMessages(changeNotifier::notifyListeners)

    @Synchronized
    override fun get(key: String): String? {
        return if (transactionValues?.contains(key) == true) transactionValues!![key] else values[key]
    }

    @Synchronized
    override fun getAll(keys: List<String>): List<String?> {
        return keys.map { get(it) }
    }

    @Synchronized
    override fun getAll(): Map<String, String?> {
        return values + (transactionValues ?: emptyMap())
    }

    @Synchronized
    override fun getAll(keys: Set<String>): Map<String, String?> {
        return keys.associateWith { get(it) }
    }

    @Synchronized
    override fun put(key: String, value: String?, silent: Boolean) {
        runTransaction {
            (transactionValues ?: values)[key] = value
            if (!silent) {
                pendingChangeMessages.entryChanged(key)
            }
        }
    }

    @Synchronized
    override fun putAll(entries: Map<String, String?>, silent: Boolean) {
        runTransaction {
            for ((key, value) in entries) {
                put(key, value, silent)
            }
        }
    }

    @Synchronized
    override fun listen(key: String, listener: IKeyListener) {
        changeNotifier.addListener(key, listener)
    }

    @Synchronized
    override fun removeListener(key: String, listener: IKeyListener) {
        changeNotifier.removeListener(key, listener)
    }

    @Synchronized
    override fun generateId(key: String): Long {
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
                    values.putAll(transactionValues!!)
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
