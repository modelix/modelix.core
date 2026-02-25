package org.modelix.model.oauth

import io.ktor.client.HttpClientConfig
import io.ktor.client.plugins.api.Send
import io.ktor.client.plugins.api.createClientPlugin
import io.ktor.client.request.HttpRequestBuilder
import io.ktor.client.request.takeFrom
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode

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
     * @param authConfig Authentication configuration (OAuth or token provider).
     */
    fun installAuth(
        config: HttpClientConfig<*>,
        authConfig: IAuthConfig,
    )
}

internal fun installAuthWithAuthTokenProvider(config: HttpClientConfig<*>, authTokenProvider: suspend () -> String?) {
    // Single custom plugin that handles ALL auth:
    // - Adds token to every request
    // - Retries on 401 with refreshed token
    // - Retries on 403 with refreshed token
    // This avoids conflicts with Ktor's Auth plugin which doesn't handle 403
    val authPlugin = createClientPlugin("ModelixAuthPlugin") {
        var cachedToken: String? = null
        var attemptedRefresh = false

        on(Send) { request ->
            // Get token for request if not already present
            if (request.headers[HttpHeaders.Authorization] == null) {
                val token = cachedToken ?: authTokenProvider()
                cachedToken = token
                if (token != null) {
                    request.headers.append(HttpHeaders.Authorization, "Bearer $token")
                }
            }

            val call = proceed(request)
            val status = call.response.status

            // Retry on 401 or 403 with a fresh token
            if ((status == HttpStatusCode.Unauthorized || status == HttpStatusCode.Forbidden) && !attemptedRefresh) {
                attemptedRefresh = true

                // Get fresh token
                val freshToken = authTokenProvider()

                if (freshToken != null && freshToken != cachedToken) {
                    cachedToken = freshToken

                    // Copy request using takeFrom (copies method, url, body, headers)
                    val newRequest = HttpRequestBuilder().takeFrom(request)

                    // Replace Authorization header with fresh token
                    newRequest.headers.remove(HttpHeaders.Authorization)
                    newRequest.headers.append(HttpHeaders.Authorization, "Bearer $freshToken")

                    proceed(newRequest)
                } else {
                    call
                }
            } else {
                // Reset flag on successful responses
                @Suppress("MagicNumber")
                if (status.value in 200..299) {
                    attemptedRefresh = false
                }
                call
            }
        }
    }

    config.apply {
        install(authPlugin)
    }
}
