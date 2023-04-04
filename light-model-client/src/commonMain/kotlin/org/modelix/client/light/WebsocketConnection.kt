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
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.ClosedReceiveChannelException
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.modelix.model.server.api.MessageFromClient
import org.modelix.model.server.api.MessageFromServer
import kotlin.time.Duration.Companion.seconds

class WebsocketConnection(val httpClient: HttpClient, val url: String) : LightModelClient.IConnection {
    private val coroutineScope = CoroutineScope(Dispatchers.Default)
    private val outgoingMessagesChannel = Channel<MessageFromClient>(capacity = Channel.UNLIMITED)
    private var session: WebSocketSession? = null
    private var connectionExpected = false
    private var messageHandler: ((message: MessageFromServer) -> Unit)? = null
    private var disconnectHandler: (() -> Unit)? = null

    override fun sendMessage(message: MessageFromClient) {
        outgoingMessagesChannel.trySend(message)
    }

    override fun onMessage(messageHandler: (message: MessageFromServer) -> Unit) {
        if (this.messageHandler != null) throw IllegalStateException("message handler is already set")
        this.messageHandler = messageHandler
    }

    override fun onDisconnect(handler: () -> Unit) {
        if (this.disconnectHandler != null) throw IllegalStateException("disconnect handler is already set")
        this.disconnectHandler = handler
    }

    override fun disconnect() {
        val s = session ?: throw IllegalStateException("not connected")
        session = null
        connectionExpected = false
        coroutineScope.launch {
            s.close(CloseReason(CloseReason.Codes.NORMAL, "disposed"))
            while (outgoingMessagesChannel.tryReceive().isSuccess) { /* clear remaining messages */ }
        }
    }

    override fun connect() {
        if (session != null) throw IllegalStateException("already connected")
        connectionExpected = true
        coroutineScope.launch {
            while (connectionExpected) {
                try {
                    httpClient.webSocket(url) {
                        session = this
                        launch {
                            for (msg in outgoingMessagesChannel) {
                                send(msg.toJson())
                            }
                        }
                        try {
                            for (frame in incoming) {
                                when (frame) {
                                    is Frame.Text -> {
                                        val text = frame.readText()
                                        LOG.trace { "message on client: $text" }
                                        try {
                                            messageHandler?.invoke(MessageFromServer.fromJson(text))
                                        } catch (ex: Exception) {
                                            LOG.error(ex) { "Exception in message handler" }
                                        }
                                    }
                                    else -> {}
                                }
                            }
                        } catch (ex : ClosedReceiveChannelException) {
                            LOG.info(ex) { "WebSocket closed" }
                        }
                    }
                    try {
                        disconnectHandler?.invoke()
                    } catch (ex: Exception) {
                        LOG.error(ex) { "Exception in disconnect handler" }
                    }
                } catch (ex: Exception) {
                    LOG.error(ex) { "" }
                }
                delay(3.seconds)
            }
        }
    }

    companion object {
        private val LOG = mu.KotlinLogging.logger {  }
    }
}