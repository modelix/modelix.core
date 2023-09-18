/*
 * Copyright (c) 2023.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.modelix.modelql.client

import io.ktor.client.HttpClient
import io.ktor.client.HttpClientConfig
import io.ktor.client.engine.HttpClientEngine
import io.ktor.client.engine.HttpClientEngineFactory
import io.ktor.client.plugins.HttpTimeout
import kotlinx.serialization.modules.SerializersModule
import org.modelix.modelql.untyped.UntypedModelQL
import kotlin.time.Duration.Companion.minutes

abstract class ModelQLClientBuilder() {
    private var url: String? = null
    private var httpClient: HttpClient? = null
    private var httpEngine: HttpClientEngine? = null
    private var httpEngineFactory: HttpClientEngineFactory<*>? = null
    private var serializersModule: SerializersModule = UntypedModelQL.serializersModule

    fun build(): ModelQLClient {
        val config: HttpClientConfig<*>.() -> Unit = {
            install(HttpTimeout) {
                requestTimeoutMillis = 2.minutes.inWholeMilliseconds
            }
        }
        val c: HttpClient = httpClient
            ?: httpEngine?.let { HttpClient(it, config) }
            ?: HttpClient((httpEngineFactory ?: getDefaultEngineFactory()), config)
        return ModelQLClient(
            url = url ?: "http://localhost:48302/query",
            client = c,
            includedSerializersModule = serializersModule,
        )
    }

    fun url(url: String): ModelQLClientBuilder {
        this.url = url
        return this
    }

    fun serializersModule(serializersModule: SerializersModule): ModelQLClientBuilder {
        this.serializersModule = serializersModule
        return this
    }

    protected abstract fun getDefaultEngineFactory(): HttpClientEngineFactory<*>

    fun httpClient(httpClient: HttpClient): ModelQLClientBuilder {
        this.httpClient = httpClient
        return this
    }

    fun httpEngine(httpEngine: HttpClientEngine): ModelQLClientBuilder {
        this.httpEngine = httpEngine
        return this
    }

    fun httpEngine(httpEngineFactory: HttpClientEngineFactory<*>): ModelQLClientBuilder {
        this.httpEngineFactory = httpEngineFactory
        return this
    }
}

expect class PlatformSpecificModelQLClientBuilder() : ModelQLClientBuilder {
    override fun getDefaultEngineFactory(): HttpClientEngineFactory<*>
}
