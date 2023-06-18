package org.modelix.modelql.client

import io.ktor.client.engine.HttpClientEngineFactory
import io.ktor.client.engine.cio.CIO

actual class PlatformSpecificModelQLClientBuilder actual constructor() :
    ModelQLClientBuilder() {
    protected actual override fun getDefaultEngineFactory(): HttpClientEngineFactory<*> {
        return CIO
    }
}
