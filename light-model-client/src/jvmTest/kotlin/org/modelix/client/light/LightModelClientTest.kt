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
import java.util.*
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

    fun runClientTest(block: suspend (LightModelClient) -> Unit) = runTest { httpClient ->
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
                        println("connecting")
                        httpClient.webSocket("ws://localhost/json/v2/test-repo/ws") {
                            println("client connected")
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

        val client = LightModelClient(createConnection())
        block(client)
    }

    @Test
    fun setProperty() = runClientTest {  client ->
        client.wait()
        val rootNode = client.getNode(ITree.ROOT_ID.toString(16))
        client.wait()
        rootNode.setPropertyValue("name", "abc")
        client.wait()
        assertEquals("abc", rootNode.getPropertyValue("name"))
    }

    @Test
    fun addNewChild() = runClientTest {  client ->
        client.wait()
        val rootNode = client.getNode(ITree.ROOT_ID.toString(16))
        client.wait()
        val child = rootNode.addNewChild("role1", -1, null)
        client.wait()
        assertEquals(1, rootNode.getChildren("role1").toList().size)
        child.setPropertyValue("name", "xyz")
        client.wait()
        assertEquals("xyz", child.getPropertyValue("name"))
    }

    private suspend fun LightModelClient.wait() {
        withTimeout(5.seconds) {
            while (!isInitialized() || hasTemporaryIds()) {
                delay(1.milliseconds)
            }
        }
    }
}
