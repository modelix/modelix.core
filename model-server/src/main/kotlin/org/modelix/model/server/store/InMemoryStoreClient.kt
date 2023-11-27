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
import java.util.*
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
    private val listeners: MutableMap<String?, MutableSet<IKeyListener>> = HashMap()

    @Synchronized
    override fun get(key: String): String? {
        return values[key]
    }

    @Synchronized
    override fun getAll(keys: List<String>): List<String?> {
        return keys.map { values[it] }
    }

    @Synchronized
    override fun getAll(): Map<String, String?> {
        return HashMap(values)
    }

    @Synchronized
    override fun getAll(keys: Set<String>): Map<String, String?> {
        return keys.associateWith { values[it] }
    }

    @Synchronized
    override fun put(key: String, value: String?, silent: Boolean) {
        values[key] = value
        if (!silent) {
            listeners[key]?.toList()?.forEach {
                try {
                    it.changed(key, value)
                } catch (ex: Exception) {
                    println(ex.message)
                    ex.printStackTrace()
                    LOG.error("Failed to notify listeners after put '$key' = '$value'", ex)
                }
            }
        }
    }

    @Synchronized
    override fun putAll(entries: Map<String, String?>, silent: Boolean) {
        for ((key, value) in entries) {
            put(key, value, silent)
        }
    }

    @Synchronized
    override fun listen(key: String, listener: IKeyListener) {
        listeners.getOrPut(key) { LinkedHashSet() }.add(listener)
    }

    @Synchronized
    override fun removeListener(key: String, listener: IKeyListener) {
        listeners[key]?.remove(listener)
    }

    @Synchronized
    override fun generateId(key: String): Long {
        val id = generateId(get(key))
        put(key, id.toString(), false)
        return id
    }

    @Synchronized
    override fun <T> runTransaction(body: () -> T): T {
        return body()
    }

    override fun close() {
    }
}
