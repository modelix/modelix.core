package org.modelix.model.client2

import io.ktor.client.HttpClient
import io.ktor.client.HttpClientConfig
import io.ktor.client.engine.cio.CIO
import org.modelix.model.oauth.ModelixOAuthClient

actual class ModelClientV2PlatformSpecificBuilder : ModelClientV2Builder() {
    override fun configureHttpClient(config: HttpClientConfig<*>) {
        super.configureHttpClient(config)
        config.apply {
            ModelixOAuthClient.installAuth(this, baseUrl, authTokenProvider)
        }
    }

    actual override fun createHttpClient(): HttpClient {
        return HttpClient(CIO) {
            configureHttpClient(this)
        }
    }
}
