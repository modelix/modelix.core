package org.modelix.model.oauth

import io.ktor.client.HttpClientConfig

@Suppress("UndocumentedPublicClass") // already documented in the expected declaration
actual object ModelixAuthClient {
    @Suppress("UndocumentedPublicFunction") // already documented in the expected declaration
    actual fun installAuth(
        config: HttpClientConfig<*>,
        baseUrl: String,
        authTokenProvider: (suspend () -> String?)?,
        authRequestBrowser: ((url: String) -> Unit)?,
    ) {
        if (authTokenProvider != null) {
            installAuthWithAuthTokenProvider(config, authTokenProvider)
        }
    }
}
