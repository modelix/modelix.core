package org.modelix.model.client2

import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import org.modelix.model.oauth.TokenParameters

actual class ModelClientV2PlatformSpecificBuilder : ModelClientV2Builder() {
    actual override fun createHttpClient(tokenParameters: TokenParameters): HttpClient {
        return HttpClient(CIO) {
            configureHttpClient(this, tokenParameters)
        }
    }
}
