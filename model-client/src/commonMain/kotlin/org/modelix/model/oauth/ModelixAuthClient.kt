package org.modelix.model.oauth

import io.ktor.client.HttpClientConfig
import io.ktor.client.plugins.auth.Auth
import io.ktor.client.plugins.auth.providers.BearerTokens
import io.ktor.client.plugins.auth.providers.bearer

/**
 * Functions and states for authenticating to a model server.
 * Configuration differs for JS and JVM.
 */
expect class ModelixAuthClient() {
    /**
     * Function to configure the authentication for an HTTP client.
     *
     * @param config Config for the HTTP client to be created.
     *               This config will be modified to enable authentication.
     * @param baseUrl Base url of model server.
     *                Required for PKCE flow in JVM.
     */
    fun installAuth(
        config: HttpClientConfig<*>,
        authConfig: IAuthConfig,
    )
}

internal fun installAuthWithAuthTokenProvider(config: HttpClientConfig<*>, authTokenProvider: suspend () -> String?) {
    config.apply {
        install(Auth) {
            bearer {
                loadTokens {
                    authTokenProvider()?.let { authToken -> BearerTokens(authToken, "") }
                }
                refreshTokens {
                    val providedToken = authTokenProvider()
                    if (providedToken != null && providedToken != this.oldTokens?.accessToken) {
                        BearerTokens(providedToken, "")
                    } else {
                        null
                    }
                }
            }
        }
    }
}
