package org.modelix.model.oauth

import io.ktor.client.HttpClientConfig

@Suppress("UndocumentedPublicClass") // already documented in the expected declaration
actual class ModelixAuthClient {
    @Suppress("UndocumentedPublicFunction") // already documented in the expected declaration
    actual fun installAuth(
        config: HttpClientConfig<*>,
        authConfig: IAuthConfig,
    ) {
        when (authConfig) {
            is OAuthConfig -> UnsupportedOperationException("JS client doesn't support OAuth2")
            is TokenProviderAuthConfig -> installAuthWithAuthTokenProvider(config, authConfig.provider)
        }
    }
}
