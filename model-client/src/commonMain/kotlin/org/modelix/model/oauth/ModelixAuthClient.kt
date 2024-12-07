package org.modelix.model.oauth

import io.ktor.client.HttpClientConfig
import io.ktor.client.plugins.auth.Auth
import io.ktor.client.plugins.auth.providers.BearerTokens
import io.ktor.client.plugins.auth.providers.bearer

/**
 * Functions and states for authenticating to a model server.
 * Configuration differs for JS and JVM.
 */
expect object ModelixAuthClient {
    /**
     * Function to configure the authentication for an HTTP client.
     *
     * If an [authTokenProvider] is given, both implementations use the provided token for Bearer authentication.
     * This is stateless.
     *
     * If no [authTokenProvider] is given, the JS implementation does not configure authentication.
     * If no [authTokenProvider] is given,
     * the JVM implementation setups a server to perform an OAuth authorization code flow with PKCE.
     * This makes many assumptions about the model server deployment,
     * Keycloak deployment, Keycloak configuration, and the client.
     * The PKCE is hard coded to work for MPS instances inside Modelix workspaces.
     * This is stateful.
     *
     * @param config Config for the HTTP client to be created.
     *               This config will be modified to enable authentication.
     * @param baseUrl Base url of model server.
     *                Required for PKCE flow in JVM.
     * @param authTokenProvider This function will be used to initially get an auth token
     *                          and to refresh it when the old one expired.
     *                          Returning `null` cause the client to attempt the request without a token.
     */
    fun installAuth(config: HttpClientConfig<*>, baseUrl: String, authTokenProvider: (suspend () -> String?)? = null)
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
