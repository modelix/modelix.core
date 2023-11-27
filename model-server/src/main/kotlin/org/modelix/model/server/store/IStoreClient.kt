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

import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import org.modelix.model.IKeyListener
import java.io.File
import java.io.IOException
import kotlin.time.Duration.Companion.seconds

interface IStoreClient : AutoCloseable {
    operator fun get(key: String): String?
    fun getAll(keys: List<String>): List<String?>
    fun getAll(keys: Set<String>): Map<String, String?>
    fun getAll(): Map<String, String?>
    fun put(key: String, value: String?, silent: Boolean = false)
    fun putAll(entries: Map<String, String?>, silent: Boolean = false)
    fun listen(key: String, listener: IKeyListener)
    fun removeListener(key: String, listener: IKeyListener)
    fun generateId(key: String): Long
    fun <T> runTransaction(body: () -> T): T
}

suspend fun pollEntry(storeClient: IStoreClient, key: String, lastKnownValue: String?): String? {
    var result: String? = null
    coroutineScope {
        var handlerCalled = false
        val callHandler: suspend (String?) -> Unit = {
            handlerCalled = true
            result = it
        }

        val channel = Channel<Unit>(Channel.RENDEZVOUS)

        val listener = object : IKeyListener {
            override fun changed(key_: String, newValue: String?) {
                launch {
                    callHandler(newValue)
                    channel.trySend(Unit)
                }
            }
        }
        try {
            storeClient.listen(key, listener)
            if (lastKnownValue != null) {
                // This could be done before registering the listener, but
                // then we have to check it twice,
                // because the value could change between the first read and
                // registering the listener.
                // Most of the time the value will be equal to the last
                // known value.
                // Registering the listener without needing it is less
                // likely to happen.
                val value = storeClient[key]
                if (value != lastKnownValue) {
                    callHandler(value)
                    return@coroutineScope
                }
            }
            withTimeoutOrNull(25.seconds) {
                channel.receive() // wait until the listener is called
            }
        } finally {
            storeClient.removeListener(key, listener)
        }
        if (!handlerCalled) result = storeClient[key]
    }
    return result
}

fun IStoreClient.loadDump(file: File): Int {
    var n = 0
    file.useLines { lines ->
        val entries = lines.associate { line ->
            val parts = line.split("#".toRegex(), limit = 2)
            n++
            parts[0] to parts[1]
        }
        putAll(entries, silent = true)
    }
    return n
}

@Synchronized
@Throws(IOException::class)
fun IStoreClient.writeDump(file: File) {
    file.writer().use { writer ->
        for ((key, value) in getAll()) {
            if (value == null) continue
            writer.append(key)
            writer.append("#")
            writer.append(value)
            writer.append("\n")
        }
    }
}
