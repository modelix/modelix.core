package org.modelix.modelql.client

import io.ktor.client.HttpClient
import io.ktor.client.engine.HttpClientEngine
import io.ktor.client.engine.HttpClientEngineFactory
import kotlinx.serialization.modules.SerializersModule
import org.modelix.modelql.untyped.UntypedModelQL

abstract class ModelQLClientBuilder() {
    private var url: String? = null
    private var httpClient: HttpClient? = null
    private var httpEngine: HttpClientEngine? = null
    private var httpEngineFactory: HttpClientEngineFactory<*>? = null
    private var serializersModule: SerializersModule = UntypedModelQL.serializersModule

    fun build(): ModelQLClient {
        val c: HttpClient = (
            httpClient ?: (
                httpEngine?.let { HttpClient(it) } ?: (httpEngineFactory ?: getDefaultEngineFactory()).let {
                    HttpClient(
                        it
                    )
                }
                )
            ).config {
        }
        return ModelQLClient(
            url = url ?: "http://localhost:48302/query",
            client = c,
            includedSerializersModule = serializersModule
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
