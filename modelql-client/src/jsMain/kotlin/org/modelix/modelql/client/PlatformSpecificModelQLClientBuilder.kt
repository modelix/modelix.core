package org.modelix.modelql.client

import io.ktor.client.engine.*
import io.ktor.client.engine.js.*
import kotlinx.serialization.modules.SerializersModuleBuilder

actual class PlatformSpecificModelQLClientBuilder actual constructor() :
    ModelQLClientBuilder() {
    protected actual override fun getDefaultEngineFactory(): HttpClientEngineFactory<*> {
        return Js
    }
}