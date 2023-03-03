/*
 * Copyright (c) 2022.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *         http://www.apache.org/licenses/LICENSE-2.0
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
import io.ktor.websocket.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.ClosedReceiveChannelException
import kotlinx.coroutines.launch
import org.modelix.model.server.api.MessageFromClient
import org.modelix.model.server.api.MessageFromServer

class WebsocketConnection(val httpClient: HttpClient, val url: String) : LightModelClient.IConnection {
    val coroutineScope = CoroutineScope(Dispatchers.Default)
    val outgoingMessagesChannel = Channel<MessageFromClient>(capacity = Channel.UNLIMITED)
    private var session: WebSocketSession? = null
    override fun sendMessage(message: MessageFromClient) {
        outgoingMessagesChannel.trySend(message)
    }

    override fun disconnect() {
        val s = session ?: throw IllegalStateException("not connected")
        session = null
        coroutineScope.launch {
            s.close(CloseReason(CloseReason.Codes.NORMAL, "disposed"))
            while (outgoingMessagesChannel.tryReceive().isSuccess) { /* clear remaining messages */ }
        }
    }

    override fun connect(messageReceiver: (message: MessageFromServer) -> Unit) {
        if (session != null) throw IllegalStateException("already connected")
        coroutineScope.launch {
            httpClient.webSocket(url) {
                session = this
                this.launch {
                    for (msg in outgoingMessagesChannel) {
                        send(msg.toJson())
                    }
                }
                try {
                    for (frame in incoming) {
                        when (frame) {
                            is Frame.Text -> {
                                val text = frame.readText()
                                // println("message on client: $text")
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