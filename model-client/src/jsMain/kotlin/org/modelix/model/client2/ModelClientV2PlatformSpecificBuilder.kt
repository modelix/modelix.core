package org.modelix.model.client2

import io.ktor.client.*
import io.ktor.client.engine.js.*

actual class ModelClientV2PlatformSpecificBuilder : ModelClientV2Builder() {
    override fun createHttpClient(): HttpClient {
        return HttpClient(Js) {
            configureHttpClient(this)
        }
    }

    override fun configureHttpClient(config: HttpClientConfig<*>) {
        super.configureHttpClient(config)
    }
}
