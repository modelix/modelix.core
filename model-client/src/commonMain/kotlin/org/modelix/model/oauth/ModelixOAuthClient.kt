/*
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.modelix.model.oauth

import io.ktor.client.HttpClientConfig
import io.ktor.client.plugins.auth.Auth
import io.ktor.client.plugins.auth.providers.BearerTokens
import io.ktor.client.plugins.auth.providers.bearer

expect object ModelixOAuthClient {
    fun installAuth(config: HttpClientConfig<*>, baseUrl: String, authTokenProvider: (suspend () -> String?)? = null)
}

fun installAuthWithAuthTokenProvider(config: HttpClientConfig<*>, authTokenProvider: suspend () -> String?) {
    config.apply {
        install(Auth) {
            bearer {
                loadTokens {
                    val token = authTokenProvider()
                    if (token == null) {
                        null
                    } else {
                        BearerTokens(token, "")
                    }
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
