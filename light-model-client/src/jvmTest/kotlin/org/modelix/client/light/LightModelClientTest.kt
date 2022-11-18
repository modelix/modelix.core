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
import kotlinx.coroutines.channels.ClosedReceiveChannelException
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
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
        httpClient.post("http://localhost/json/test-repo/init")
        httpClient.webSocket("ws://localhost/json/v2/test-repo/ws") {
            val listeners = ArrayList<(message: MessageFromServer) -> Unit>()
            val connection = object : LightModelClient.IConnection {
                override fun sendMessage(message: MessageFromClient) {
                    runBlocking {
                        send(message.toJson())
                    }
                }

                override fun receiveMessages(listener: (message: MessageFromServer) -> Unit) {
                    listeners.add(listener)
                }
            }
            launch {
                try {
                    while(true) {
                        val messageFromServer = (incoming.receive() as? Frame.Text)?.let { MessageFromServer.fromJson(it.readText()) }
                        if (messageFromServer != null) {
                            listeners.forEach { it(messageFromServer) }
                        }
                    }
                } catch (ex : ClosedReceiveChannelException) {
                    println("WebSocket closed")
                }
            }
            val client = LightModelClient(connection)
            block(client)
        }
    }

    @Test
    fun test() = runClientTest {  client ->
        val rootNode = client.getNode(ITree.ROOT_ID.toString(16))
        delay(100.milliseconds)
        rootNode.setPropertyValue("name", "abc")
        delay(100.milliseconds)
        assertEquals("abc", rootNode.getPropertyValue("name"))
    }
}