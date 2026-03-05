package org.modelix.model.client2

import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import org.modelix.model.oauth.ITokenParameters

actual class ModelClientV2PlatformSpecificBuilder : ModelClientV2Builder() {
    actual override fun createHttpClient(tokenParameters: ITokenParameters): HttpClient {
        return HttpClient(CIO) {
            configureHttpClient(this, tokenParameters)
        }
    }
}
