package org.modelix.model.client2

import io.ktor.client.HttpClient
import io.ktor.client.engine.js.Js

actual class ModelClientV2PlatformSpecificBuilder : ModelClientV2Builder() {
    actual override fun createHttpClient(): HttpClient {
        return HttpClient(Js) {
            configureHttpClient(this)
        }
    }
}
