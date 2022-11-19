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
import kotlinx.coroutines.channels.ClosedReceiveChannelException
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.modelix.authorization.installAuthentication
import org.modelix.model.api.ITree
import org.modelix.model.server.InMemoryStoreClient
import org.modelix.model.server.JsonModelServer
import org.modelix.model.server.JsonModelServer2
import org.modelix.model.server.LocalModelClient
import org.modelix.model.server.api.MessageFromClient
import org.modelix.model.server.api.MessageFromServer
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

class LightModelClientTest {

    private fun runTest(block: suspend (HttpClient) -> Unit) = testApplication {
        application {
            installAuthentication(unitTestMode = true)
            install(io.ktor.server.websocket.WebSockets)
            val modelClient = LocalModelClient(InMemoryStoreClient())
            JsonModelServer(modelClient).init(this)
            JsonModelServer2(modelClient).init(this)
        }
        val client = createClient {
            install(WebSockets)
        }
        block(client)
    }

    fun runClientTest(block: suspend (LightModelClient, LightModelClient) -> Unit) = runTest { httpClient ->
        val response = httpClient.post("http://localhost/json/test-repo/init").status
        println("init: $response")

        val createConnection: ()->LightModelClient.IConnection = {
            object : LightModelClient.IConnection {
                var wsSession: DefaultClientWebSocketSession? = null
                val coroutineScope = CoroutineScope(Dispatchers.Default)
                override fun sendMessage(message: MessageFromClient) {
                    runBlocking {
                        (wsSession ?: throw IllegalStateException("Not connected")).send(message.toJson())
                    }
                }

                override fun connect(messageReceiver: (message: MessageFromServer) -> Unit) {
                    coroutineScope.launch {
                        httpClient.webSocket("ws://localhost/json/v2/test-repo/ws") {
                            wsSession = this
                            try {
                                for (frame in incoming) {
                                    when (frame) {
                                        is Frame.Text -> {
                                            val text = frame.readText()
                                            println("message: $text")
                                            messageReceiver(MessageFromServer.fromJson(text))
                                        }
                                        else -> {}
                                    }
                                }
                            } catch (ex : ClosedReceiveChannelException) {
                                println("WebSocket closed")
                            }
                        }
                    }
                }
            }
        }

        val client1 = LightModelClient(createConnection())
        val client2 = LightModelClient(createConnection())
        wait {client1.isInitialized() && client2.isInitialized() }
        block(client1, client2)
    }

    @Test
    fun setProperty() = runClientTest {  client1, client2 ->
        val role = "name"
        val newValue = "abc"
        val rootNode1 = client1.getNode(ITree.ROOT_ID.toString(16))
        val rootNode2 = client2.getNode(ITree.ROOT_ID.toString(16))
        rootNode1.setPropertyValue(role, newValue)
        assertEquals(newValue, rootNode1.getPropertyValue(role))
        wait { rootNode2.getPropertyValue(role) == newValue }
        assertEquals(newValue, rootNode2.getPropertyValue(role))
    }

    @Test
    fun addNewChild() = runClientTest {  client1, client2 ->
        val rootNode1 = client1.getNode(ITree.ROOT_ID.toString(16))
        val rootNode2 = client2.getNode(ITree.ROOT_ID.toString(16))
        val child1 = rootNode1.addNewChild("role1", -1, null)
        assertEquals(1, rootNode1.getChildren("role1").toList().size)
        assertEquals(0, rootNode2.getChildren("role1").toList().size)
        wait { rootNode2.getChildren("role1").toList().size == 1 }
        assertEquals(1, rootNode2.getChildren("role1").toList().size)

        val child2 = rootNode2.getChildren("role1").first()
        child2.setPropertyValue("name", "xyz")
        assertEquals("xyz", child2.getPropertyValue("name"))
        assertEquals(null, child1.getPropertyValue("name"))
        wait { child1.getPropertyValue("name") == "xyz" }
        assertEquals("xyz", child1.getPropertyValue("name"))
    }

    private suspend fun wait(condition: ()->Boolean) {
        withTimeout(5.seconds) {
            while (!condition()) {
                delay(1.milliseconds)
            }
        }
    }
}
