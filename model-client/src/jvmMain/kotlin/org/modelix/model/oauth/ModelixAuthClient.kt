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
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMessage
import io.ktor.http.auth.HttpAuthHeader
import io.ktor.http.auth.parseAuthorizationHeader
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Suppress("UndocumentedPublicClass") // already documented in the expected declaration
actual object ModelixAuthClient {
    private var DATA_STORE_FACTORY: DataStoreFactory = MemoryDataStoreFactory()
    private val HTTP_TRANSPORT: HttpTransport = NetHttpTransport()
    private val JSON_FACTORY: JsonFactory = GsonFactory()

    fun getTokens(): StoredCredential? {
        return StoredCredential.getDefaultDataStore(DATA_STORE_FACTORY).get("user")
    }

    suspend fun authorize(modelixServerUrl: String): Credential {
        val oidcUrl = modelixServerUrl.trimEnd('/') + "/realms/modelix/protocol/openid-connect"
        return authorize(
            clientId = "external-mps",
            scopes = listOf("email"),
            authUrl = "$oidcUrl/auth",
            tokenUrl = "$oidcUrl/token",
            authRequestBrowser = null,
        )
    }

    suspend fun authorize(
        clientId: String,
        scopes: List<String>,
        authUrl: String,
        tokenUrl: String,
        authRequestBrowser: ((url: String) -> Unit)?,
    ): Credential {
        return withContext(Dispatchers.IO) {
            val flow = AuthorizationCodeFlow.Builder(
                BearerToken.authorizationHeaderAccessMethod(),
                HTTP_TRANSPORT,
                JSON_FACTORY,
                GenericUrl(tokenUrl),
                ClientParametersAuthentication(clientId, null),
                clientId,
                authUrl,
            )
                .setScopes(scopes)
                .enablePKCE()
                .setDataStoreFactory(DATA_STORE_FACTORY)
                .build()
            val receiver: LocalServerReceiver = LocalServerReceiver.Builder().setHost("127.0.0.1").build()
            val browser = authRequestBrowser?.let {
                object : AuthorizationCodeInstalledApp.Browser {
                    override fun browse(url: String) {
                        it(url)
                    }
                }
            } ?: AuthorizationCodeInstalledApp.DefaultBrowser()
            AuthorizationCodeInstalledApp(flow, receiver, browser).authorize("user")
        }
    }

    @Suppress("UndocumentedPublicFunction") // already documented in the expected declaration
    actual fun installAuth(
        config: HttpClientConfig<*>,
        baseUrl: String,
        authTokenProvider: (suspend () -> String?)?,
        authRequestBrowser: ((url: String) -> Unit)?,
    ) {
        if (authTokenProvider != null) {
            installAuthWithAuthTokenProvider(config, authTokenProvider)
        } else {
            installAuthWithPKCEFlow(config, baseUrl, authRequestBrowser)
        }
    }

    private fun installAuthWithPKCEFlow(
        config: HttpClientConfig<*>,
        baseUrl: String,
        authRequestBrowser: ((url: String) -> Unit)?,
    ) {
        config.apply {
            install(Auth) {
                bearer {
                    loadTokens {
                        getTokens()?.let { BearerTokens(it.accessToken, it.refreshToken) }
                    }
                    refreshTokens {
                        val tokens = response.parseWWWAuthenticate()?.let { wwwAuthenticate ->
                            // The model server tells the client where to get a token

                            if (wwwAuthenticate.parameter("error") != "invalid_token") return@let null
                            val authUrl = wwwAuthenticate.parameter("authorization_uri") ?: return@let null
                            val tokenUrl = wwwAuthenticate.parameter("token_uri") ?: return@let null
                            val realm = wwwAuthenticate.parameter("realm")
                            val description = wwwAuthenticate.parameter("error_description")
                            authorize(
                                clientId = "modelix-sync-plugin",
                                scopes = listOf("sync"),
                                authUrl = authUrl,
                                tokenUrl = tokenUrl,
                                authRequestBrowser = authRequestBrowser,
                            )
                        } ?: let {
                            // legacy keycloak specific URLs

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
                            authorize(url)
                        }

                        println("Access token: ${tokens.accessToken}")
                        println("Refresh token: ${tokens.refreshToken}")
                        BearerTokens(tokens.accessToken, tokens.refreshToken)
                    }
                }
            }
        }
    }

    fun HttpMessage.parseWWWAuthenticate(): HttpAuthHeader.Parameterized? {
        return headers[HttpHeaders.WWWAuthenticate]
            ?.let { parseAuthorizationHeader(it) as? HttpAuthHeader.Parameterized }
    }
}
