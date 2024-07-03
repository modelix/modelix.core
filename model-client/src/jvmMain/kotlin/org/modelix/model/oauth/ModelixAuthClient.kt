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

import com.google.api.client.auth.oauth2.AuthorizationCodeFlow
import com.google.api.client.auth.oauth2.BearerToken
import com.google.api.client.auth.oauth2.ClientParametersAuthentication
import com.google.api.client.auth.oauth2.Credential
import com.google.api.client.auth.oauth2.StoredCredential
import com.google.api.client.extensions.java6.auth.oauth2.AuthorizationCodeInstalledApp
import com.google.api.client.extensions.jetty.auth.oauth2.LocalServerReceiver
import com.google.api.client.http.GenericUrl
import com.google.api.client.http.HttpTransport
import com.google.api.client.http.javanet.NetHttpTransport
import com.google.api.client.json.JsonFactory
import com.google.api.client.json.gson.GsonFactory
import com.google.api.client.util.store.DataStoreFactory
import com.google.api.client.util.store.MemoryDataStoreFactory
import io.ktor.client.HttpClientConfig
import io.ktor.client.plugins.auth.Auth
import io.ktor.client.plugins.auth.providers.BearerTokens
import io.ktor.client.plugins.auth.providers.bearer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Suppress("UndocumentedPublicClass") // already documented in the expected declaration
actual object ModelixAuthClient {
    private var DATA_STORE_FACTORY: DataStoreFactory = MemoryDataStoreFactory()
    private val SCOPE = "email"
    private val HTTP_TRANSPORT: HttpTransport = NetHttpTransport()
    private val JSON_FACTORY: JsonFactory = GsonFactory()

    fun getTokens(): StoredCredential? {
        return StoredCredential.getDefaultDataStore(DATA_STORE_FACTORY).get("user")
    }

    suspend fun authorize(modelixServerUrl: String): Credential {
        return withContext(Dispatchers.IO) {
            val oidcUrl = modelixServerUrl.trimEnd('/') + "/realms/modelix/protocol/openid-connect"
            val clientId = "external-mps"
            val flow = AuthorizationCodeFlow.Builder(
                BearerToken.authorizationHeaderAccessMethod(),
                HTTP_TRANSPORT,
                JSON_FACTORY,
                GenericUrl("$oidcUrl/token"),
                ClientParametersAuthentication(clientId, null),
                clientId,
                "$oidcUrl/auth",
            )
                .setScopes(listOf(SCOPE))
                .enablePKCE()
                .setDataStoreFactory(DATA_STORE_FACTORY)
                .build()
            val receiver: LocalServerReceiver = LocalServerReceiver.Builder().setHost("127.0.0.1").build()
            AuthorizationCodeInstalledApp(flow, receiver).authorize("user")
        }
    }

    @Suppress("UndocumentedPublicFunction") // already documented in the expected declaration
    actual fun installAuth(config: HttpClientConfig<*>, baseUrl: String, authTokenProvider: (suspend () -> String?)?) {
        if (authTokenProvider != null) {
            installAuthWithAuthTokenProvider(config, authTokenProvider)
        } else {
            installAuthWithPKCEFlow(config, baseUrl)
        }
    }

    private fun installAuthWithPKCEFlow(config: HttpClientConfig<*>, baseUrl: String) {
        config.apply {
            install(Auth) {
                bearer {
                    loadTokens {
                        getTokens()?.let { BearerTokens(it.accessToken, it.refreshToken) }
                    }
                    refreshTokens {
                        var url = baseUrl
                        if (!url.endsWith("/")) url += "/"
                        // XXX Detecting and removing "/model/" is workaround for when the model server
                        // is used in Modelix workspaces and reachable behind the sub path /model/".
                        // When the model server is reachable at https://example.org/model/,
                        // Keycloak is expected to be reachable under https://example.org/realms/
                        // See https://github.com/modelix/modelix.kubernetes/blob/60f7db6533c3fb82209b1a6abb6836923f585672/proxy/nginx.conf#L14
                        // and https://github.com/modelix/modelix.kubernetes/blob/60f7db6533c3fb82209b1a6abb6836923f585672/proxy/nginx.conf#L41
                        // TODO MODELIX-975 remove this check and replace with configuration.
                        if (url.endsWith("/model/")) url = url.substringBeforeLast("/model/")
                        val tokens = authorize(url)
                        BearerTokens(tokens.accessToken, tokens.refreshToken)
                    }
                }
            }
        }
    }
}
