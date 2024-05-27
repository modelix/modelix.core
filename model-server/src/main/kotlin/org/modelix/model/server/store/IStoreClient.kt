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

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import org.modelix.model.IGenericKeyListener
import kotlin.time.Duration.Companion.seconds

interface IStoreClient : IGenericStoreClient<String>

suspend fun <T> IStoreClient.runTransactionSuspendable(body: () -> T): T {
    return withContext(Dispatchers.IO) { runTransaction(body) }
}

suspend fun pollEntry(storeClient: IsolatingStore, key: ObjectInRepository, lastKnownValue: String?): String? {
    var result: String? = null
    coroutineScope {
        var handlerCalled = false
        val callHandler: suspend (String?) -> Unit = {
            handlerCalled = true
            result = it
        }

        val channel = Channel<Unit>(Channel.RENDEZVOUS)

        val listener = object : IGenericKeyListener<ObjectInRepository> {
            override fun changed(key: ObjectInRepository, value: String?) {
                launch {
                    callHandler(value)
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
