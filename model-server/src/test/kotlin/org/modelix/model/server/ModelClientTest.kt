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

package org.modelix.model.server

import io.ktor.server.application.install
import io.ktor.server.resources.Resources
import io.ktor.server.routing.IgnoreTrailingSlash
import io.ktor.server.testing.ApplicationTestBuilder
import io.ktor.server.testing.testApplication
import kotlinx.coroutines.delay
import kotlinx.coroutines.withTimeout
import org.modelix.authorization.installAuthentication
import org.modelix.model.IKeyListener
import org.modelix.model.client.RestWebModelClient
import org.modelix.model.server.handlers.KeyValueLikeModelServer
import org.modelix.model.server.handlers.RepositoriesManager
import org.modelix.model.server.store.InMemoryStoreClient
import org.modelix.model.server.store.LocalModelClient
import org.modelix.model.server.store.forGlobalRepository
import java.util.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.fail
import kotlin.time.Duration.Companion.seconds

class ModelClientTest {

    private fun runTest(block: suspend ApplicationTestBuilder.() -> Unit) = testApplication {
        application {
            installAuthentication(unitTestMode = true)
            install(Resources)
            install(IgnoreTrailingSlash)
            KeyValueLikeModelServer(RepositoriesManager(LocalModelClient(InMemoryStoreClient().forGlobalRepository()))).init(this)
        }
        block()
    }

    @Test
    fun test_t1() = runTest {
        val numClients = 3
        val numListenersPerClient = 3
        val numKeys = numListenersPerClient * 2

        val rand = Random(67845)
        val clients = (0 until numClients).map { createModelClient() }
        val listeners: MutableList<Listener> = ArrayList()
        val expected: MutableMap<String, String> = HashMap()
        for (client in clients.withIndex()) {
            for (i in 0 until numListenersPerClient) {
                println("Phase A: client $client i=$i of ${clients.size}")
//                delay(50)
                val key = "test_$i"
                val l = Listener(key, client.value, client.index, i)
                client.value.listen(key, l)
                listeners.add(l)
            }
        }
        delay(3.seconds)
        for (i in (1..2).flatMap { 0 until numKeys }) {
            println("Phase B: i=$i of $numKeys")
            val key = "test_$i"
            val value = rand.nextLong().toString()
            expected[key] = value
            println(" put $key = $value")
            val writingClientIndex = rand.nextInt(clients.size)
            println(" client is $writingClientIndex")
            try {
                clients[writingClientIndex].putA(key, value)
            } catch (e: Exception) {
                System.err.println(e.message)
                e.printStackTrace(System.err)
                throw e
            }
            println(" put to client $writingClientIndex")
            for (client in clients) {
                withTimeout(1.seconds) {
                    assertEquals(expected[key], client.getA(key))
                }
            }
            println(" verified")
            for (timeout in 0..30) {
                if (listeners.all { expected[it.key] == it.lastValue }) {
                    println("All changes received after ${timeout * 100} ms")
                    break
                }
                delay(100)
            }
            val unsuccessfulListeners = listeners.filter { expected[it.key] != it.lastValue }
            if (unsuccessfulListeners.isNotEmpty()) {
                fail("change not received by listeners: $unsuccessfulListeners")
            }
            println(" verified also on listeners")
        }
        for (client in clients) {
            client.dispose()
        }
    }

    @Test
    fun `can retrieve server id initially`() = runTest {
        val modelClient = createModelClient()

        val serverId = modelClient.getA("server-id")

        assertNotNull(serverId)
    }

    private fun ApplicationTestBuilder.createModelClient(): RestWebModelClient {
        val url = "http://localhost/"
        return RestWebModelClient(baseUrl = url, providedClient = client)
    }

    inner class Listener(var key: String, private val client: RestWebModelClient, val clientIndex: Int, val listenerIndex: Int) : IKeyListener {
        var lastValue: String? = null
        override fun changed(key: String, value: String?) {
            lastValue = value
            println("+change " + this + " / " + this.key + " / " + key + " = " + value)
        }
        override fun toString() = "$clientIndex.$listenerIndex:$key"
    }
}
