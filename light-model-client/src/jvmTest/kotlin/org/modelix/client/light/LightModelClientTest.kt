/*
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.modelix.client.light

import io.ktor.client.*
import io.ktor.client.plugins.websocket.*
import io.ktor.client.request.*
import io.ktor.server.application.*
import io.ktor.server.testing.*
import io.ktor.websocket.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.ClosedReceiveChannelException
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout
import org.modelix.authorization.installAuthentication
import org.modelix.model.api.addNewChild
import org.modelix.model.api.getDescendants
import org.modelix.model.server.InMemoryStoreClient
import org.modelix.model.server.JsonModelServer
import org.modelix.model.server.JsonModelServer2
import org.modelix.model.server.LocalModelClient
import org.modelix.model.server.api.MessageFromClient
import org.modelix.model.server.api.MessageFromServer
import org.modelix.model.test.RandomModelChangeGenerator
import kotlin.random.Random
import kotlin.test.Ignore
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

class LightModelClientTest {
    var localModelClient: LocalModelClient? = null

    private fun runTest(block: suspend (HttpClient) -> Unit) = testApplication {
        application {
            installAuthentication(unitTestMode = true)
            install(io.ktor.server.websocket.WebSockets)
            val modelClient = LocalModelClient(InMemoryStoreClient())
            localModelClient = modelClient
            JsonModelServer(modelClient).init(this)
            JsonModelServer2(modelClient).init(this)
        }
        val client = createClient {
            install(WebSockets)
        }
        block(client)
    }

    fun runClientTest(block: suspend (suspend (debugName: String)->LightModelClient) -> Unit) = runTest { httpClient ->
        withTimeout(2.minutes) {
            val response = httpClient.post("http://localhost/json/test-repo/init").status
            //println("init: $response")

            val createConnection: ()->LightModelClient.IConnection = {
                WebsocketConnection(httpClient, "ws://localhost/json/v2/test-repo/ws")
            }

            val createClient: suspend (debugName: String)->LightModelClient = { debugName ->
                val client = LightModelClient(createConnection(), debugName)
                wait {client.isInitialized() }
                client
            }
            block(createClient)
        }
    }

    @Test
    fun setProperty() = runClientTest {  createClient ->
        val client1 = createClient("1")
        val client2 = createClient("2")
        val role = "name"
        val newValue = "abc"
        val rootNode1 = client1.runWrite {
            val rootNode = client1.getRootNode()!!
            rootNode.setPropertyValue(role, newValue)
            rootNode
        }
        assertEquals(newValue, client1.runRead { rootNode1.getPropertyValue(role) })
        val rootNode2 = client2.runRead { client2.getRootNode()!! }
        wait { client2.runRead { rootNode2.getPropertyValue(role) } == newValue }
        assertEquals(newValue, client2.runRead { rootNode2.getPropertyValue(role) })
    }

    @Test
    fun addNewChild() = runClientTest { createClient ->
        val client1 = createClient("1")
        val client2 = createClient("2")

        val rootNode1 = client1.runRead { client1.getRootNode()!! }
        val rootNode2 = client2.runRead { client2.getRootNode()!! }
        assertEquals(0, client2.runRead { rootNode2.getChildren("role1").toList().size })
        val child1 = client1.runWrite { rootNode1.addNewChild("role1") }
        assertEquals(1, client1.runRead { rootNode1.getChildren("role1").toList().size })
        wait {
            client1.checkException()
            client2.checkException()
            client2.runRead { rootNode2.getChildren("role1").toList().size == 1 }
        }
        assertEquals(1, client2.runRead { rootNode2.getChildren("role1").toList().size })

        val child2 = client2.runRead { rootNode2.getChildren("role1").first() }
        assertEquals(null, client1.runRead { child1.getPropertyValue("name") })
        client2.runWrite { child2.setPropertyValue("name", "xyz") }
        assertEquals("xyz", client2.runRead { child2.getPropertyValue("name") })
        wait { client1.runRead { child1.getPropertyValue("name") == "xyz" } }
        assertEquals("xyz", client1.runRead { child1.getPropertyValue("name") })
    }

    @Test
    fun random() = runClientTest { createClient ->
        val client1 = createClient("1")

        val rand = Random(1234L)
        val changeGenerator = RandomModelChangeGenerator(client1.runRead { client1.getRootNode()!! }, rand)
        for (i in (1..1000)) {
            client1.runWrite {
                for (k in (0..rand.nextInt(1, 10))) {
                    changeGenerator.applyRandomChange()
                    client1.checkException()
                }
            }
            if (rand.nextInt(5) == 0) wait { client1.isInSync() }
        }
        wait { client1.isInSync() }
        client1.checkException()
    }

    @Ignore("unstable")
    @Test
    fun random2clients() = runClientTest { createClient ->
        val client1 = createClient("1")
        val client2 = createClient("2")

        val rand = Random(1234L)
        val changeGenerator1 = RandomModelChangeGenerator(client1.runRead { client1.getRootNode()!! }, rand)
        for (i in (1..200)) {
            wait { client1.isInSync() && client2.isInSync() }
            client1.runWrite {
                for (k in (0..rand.nextInt(1,10))) {
                    changeGenerator1.applyRandomChange()
                    client1.checkException()
                }
            }
            wait { client1.isInSync() && client2.isInSync() }
            //if (rand.nextInt(5) == 0) wait { client2.isInSync() }
        }

        wait { client1.isInSync() && client2.isInSync() }
        compareClients(client1, client2)

        println("starting write to client 2")
        val changeGenerator2 = RandomModelChangeGenerator(client2.runRead { client2.getRootNode()!! }, rand)
        for (i in (1..200)) {
            wait { client1.isInSync() && client2.isInSync() }
            client2.runWrite {
                for (k in (0..rand.nextInt(1,10))) {
                    changeGenerator2.applyRandomChange()
                    client2.checkException()
                }
            }
            wait { client1.isInSync() && client2.isInSync() }
        }

        wait { client1.isInSync() && client2.isInSync() }
        compareClients(client1, client2)
    }

    @Ignore("unstable")
    @Test
    fun random2clientsMixed() = runClientTest { createClient ->
        val client1 = createClient("1")
        val client2 = createClient("2")

        coroutineScope {
            launch {
                val rand1 = Random(87634L)
                val changeGenerator1 = RandomModelChangeGenerator(client1.runRead { client1.getRootNode()!! }, rand1)
                for (i in (1..10)) {
                    client1.runWrite {
                        for (k in (0..rand1.nextInt(1,10))) {
                            changeGenerator1.applyRandomChange()
                            client1.checkException()
                        }
                    }
                    delay(rand1.nextLong(0, 5))
                }
                client1.runWrite {
                    client1.getRootNode()!!.setPropertyValue("client1done", "true")
                }
            }
            launch {
                val rand2 = Random(87235778L)
                val changeGenerator2 = RandomModelChangeGenerator(client2.runRead { client2.getRootNode()!! }, rand2)
                for (i in (1..10)) {
                    client2.runWrite {
                        for (k in (0..rand2.nextInt(1,10))) {
                            changeGenerator2.applyRandomChange()
                            client2.checkException()
                        }
                    }
                    delay(rand2.nextLong(0, 5))
                }
                client2.runWrite {
                    client2.getRootNode()!!.setPropertyValue("client2done", "true")
                }
            }
        }

        println("writing done")
        wait { client1.isInSync() && client2.isInSync() }
        wait {
            client1.runRead { client1.getRootNode()!!.getPropertyValue("client2done") } == "true"
                    && client2.runRead { client2.getRootNode()!!.getPropertyValue("client1done") } == "true"
        }
        compareClients(client1, client2)
    }

    private fun compareClients(client1: LightModelClient, client2: LightModelClient) {
        client1.runRead { client2.runRead {
            val nodes1 = client1.getRootNode()!!.getDescendants(true).sortedBy { (it as LightModelClient.NodeAdapter).nodeId }.toList()
            val nodes2 = client2.getRootNode()!!.getDescendants(true).sortedBy { (it as LightModelClient.NodeAdapter).nodeId }.toList()
            assertEquals(nodes1.size, nodes2.size)
            assertEquals(
                nodes1.map { (it as LightModelClient.NodeAdapter).nodeId },
                nodes2.map { (it as LightModelClient.NodeAdapter).nodeId }
            )
        }}
    }

    private suspend fun wait(condition: ()->Boolean) {
        withTimeout(30.seconds) {
            while (!condition()) {
                delay(1.milliseconds)
            }
        }
    }
}
