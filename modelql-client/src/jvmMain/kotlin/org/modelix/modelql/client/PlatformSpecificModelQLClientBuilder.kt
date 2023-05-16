package org.modelix.modelql.client

import io.ktor.client.engine.*
import io.ktor.client.engine.cio.*
import kotlinx.serialization.modules.SerializersModuleBuilder
import org.modelix.model.api.INode

actual class PlatformSpecificModelQLClientBuilder actual constructor() :
    ModelQLClientBuilder() {
    protected actual override fun getDefaultEngineFactory(): HttpClientEngineFactory<*> {
        return CIO
    }
}