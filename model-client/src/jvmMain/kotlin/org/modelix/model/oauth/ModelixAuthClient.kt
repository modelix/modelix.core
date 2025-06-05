package org.modelix.model.oauth

import com.google.api.client.auth.oauth2.AuthorizationCodeFlow
import com.google.api.client.auth.oauth2.BearerToken
import com.google.api.client.auth.oauth2.ClientParametersAuthentication
import com.google.api.client.auth.oauth2.Credential
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
import io.ktor.client.plugins.auth.providers.RefreshTokensParams
import io.ktor.client.plugins.auth.providers.bearer
import io.ktor.client.statement.request
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMessage
import io.ktor.http.URLProtocol
import io.ktor.http.Url
import io.ktor.http.auth.HttpAuthHeader
import io.ktor.http.auth.parseAuthorizationHeader
import io.ktor.http.buildUrl
import io.ktor.http.isSecure
import io.ktor.http.takeFrom
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.modelix.kotlin.utils.urlEncode
import java.net.SocketException

@Suppress("UndocumentedPublicClass") // already documented in the expected declaration
actual class ModelixAuthClient {
    companion object {
        private val LOG = mu.KotlinLogging.logger { }
    }
    private var DATA_STORE_FACTORY: DataStoreFactory = MemoryDataStoreFactory()
    private val HTTP_TRANSPORT: HttpTransport = NetHttpTransport()
    private val JSON_FACTORY: JsonFactory = GsonFactory()
    private val userId = "modelix-user"
    private var lastCredentials: Credential? = null

    fun getTokens(): Credential? {
        return lastCredentials?.refreshIfExpired()?.takeIf { !it.isExpired() }
    }

    private fun Credential.isExpired() = (expiresInSeconds ?: 0) < 60

    private fun Credential.refreshIfExpired(): Credential {
        if (isExpired()) {
            refreshToken()
        }
        return this
    }

    suspend fun authorize(config: OAuthConfig): Credential {
        return withContext(Dispatchers.IO) {
            val flow = AuthorizationCodeFlow.Builder(
                BearerToken.authorizationHeaderAccessMethod(),
                HTTP_TRANSPORT,
                JSON_FACTORY,
                GenericUrl(config.tokenUrl),
                ClientParametersAuthentication(config.clientId, config.clientSecret),
                config.clientId,
                config.authorizationUrl,
            )
                .setScopes(config.scopes)
                .enablePKCE()
                .setDataStoreFactory(DATA_STORE_FACTORY)
                .build()

            val existingTokens = flow.loadCredential(userId)?.refreshIfExpired()
            if (existingTokens?.isExpired() == false) return@withContext existingTokens

            repeat(100) { n ->
                val port = 26815 + n
                try {
                    val receiver: LocalServerReceiver = LocalServerReceiver.Builder().setHost("localhost").setPort(port).build()
                    val browser = config.authRequestHandler?.let {
                        object : AuthorizationCodeInstalledApp.Browser {
                            override fun browse(url: String) {
                                it.browse(url)
                            }
                        }
                    } ?: AuthorizationCodeInstalledApp.DefaultBrowser()
                    val tokens = AuthorizationCodeInstalledApp(flow, receiver, browser).authorize(userId)
                    if ((tokens.expiresInSeconds ?: 0) < 60) {
                        tokens.refreshToken()
                    }
                    return@withContext tokens
                } catch (ex: SocketException) {
                    LOG.info("Port $port already in use")
                    // Port is already in use. Try next one.
                }
            }
            throw IllegalStateException("Couldn't find an available port for the redirect URL")
        }
    }

    @Suppress("UndocumentedPublicFunction") // already documented in the expected declaration
    actual fun installAuth(
        config: HttpClientConfig<*>,
        authConfig: IAuthConfig,
    ) {
        when (authConfig) {
            is TokenProviderAuthConfig -> installAuthWithAuthTokenProvider(config, authConfig.provider)
            is OAuthConfig -> installAuthWithPKCEFlow(config, authConfig)
        }
    }

    private fun installAuthWithPKCEFlow(
        config: HttpClientConfig<*>,
        authConfig: OAuthConfig,
    ) {
        fun String.fillParameters(): String {
            return if (authConfig.repositoryId == null) {
                this
            } else {
                replace("{repositoryId}", authConfig.repositoryId.id.urlEncode())
            }
        }

        config.apply {
            install(Auth) {
                bearer {
                    loadTokens {
                        getTokens()?.let { BearerTokens(it.accessToken, it.refreshToken) }
                    }
                    refreshTokens {
                        try {
                            val tokens = response.parseWWWAuthenticate()?.let { wwwAuthenticate ->
                                // The model server tells the client where to get a token

                                if (wwwAuthenticate.parameter("error") != "invalid_token") return@let null
                                val updatedConfig = authConfig.copy(
                                    authorizationUrl = authConfig.authorizationUrl
                                        ?: useSameProtocol(wwwAuthenticate.parameter("authorization_uri") ?: return@let null).fillParameters(),
                                    tokenUrl = authConfig.tokenUrl
                                        ?: useSameProtocol(wwwAuthenticate.parameter("token_uri") ?: return@let null).fillParameters(),
                                )
                                val realm = wwwAuthenticate.parameter("realm")
                                val description = wwwAuthenticate.parameter("error_description")
                                authorize(updatedConfig)
                            } ?: authorize(authConfig)

                            LOG.info("Access Token: " + tokens.accessToken)

                            BearerTokens(tokens.accessToken, tokens.refreshToken ?: oldTokens?.refreshToken)
                        } catch (ex: Throwable) {
                            LOG.error(ex) { "Token refresh failed" }
                            throw ex
                        }
                    }
                }
            }
        }
    }

    fun HttpMessage.parseWWWAuthenticate(): HttpAuthHeader.Parameterized? {
        // The Amazon API Gateway replaces the WWW-Authenticate header with x-amzn-remapped-www-authenticate
        // to prevent any login popup in the browser. REST clients are expected to read this non-standard header.
        return (headers[HttpHeaders.WWWAuthenticate] ?: headers["x-amzn-remapped-www-authenticate"])
            ?.let { parseAuthorizationHeader(it) as? HttpAuthHeader.Parameterized }
    }

    /**
     * In test environments https often doesn't have a valid certificate.
     * Use http for authorization, if the original request itself uses http.
     */
    private fun RefreshTokensParams.useSameProtocol(url: String): String {
        val needSecureProtocol = response.request.url.protocol.isSecure()
        if (Url(url).protocol.isSecure() == needSecureProtocol) return url
        return buildUrl {
            takeFrom(url)
            protocol = when (protocol) {
                URLProtocol.HTTPS, URLProtocol.HTTP -> if (needSecureProtocol) URLProtocol.HTTPS else URLProtocol.HTTP
                URLProtocol.WSS, URLProtocol.WS -> if (needSecureProtocol) URLProtocol.WSS else URLProtocol.WS
                else -> protocol
            }
        }.toString()
    }
}
