package org.modelix.model.oauth

import com.google.api.client.auth.oauth2.AuthorizationCodeFlow
import com.google.api.client.auth.oauth2.BearerToken
import com.google.api.client.auth.oauth2.ClientParametersAuthentication
import com.google.api.client.auth.oauth2.Credential
import com.google.api.client.auth.oauth2.TokenResponseException
import com.google.api.client.extensions.java6.auth.oauth2.AuthorizationCodeInstalledApp
import com.google.api.client.extensions.jetty.auth.oauth2.LocalServerReceiver
import com.google.api.client.http.GenericUrl
import com.google.api.client.http.HttpTransport
import com.google.api.client.http.javanet.NetHttpTransport
import com.google.api.client.json.JsonFactory
import com.google.api.client.json.gson.GsonFactory
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
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.modelix.kotlin.utils.runSynchronized
import org.modelix.kotlin.utils.urlEncode
import java.net.SocketException
import java.net.SocketTimeoutException
import java.util.Collections

@Suppress("UndocumentedPublicClass") // already documented in the expected declaration
actual class ModelixAuthClient {
    companion object {
        private val LOG = mu.KotlinLogging.logger { }
    }

    private class CachedTokens {
        private val httpTransport: HttpTransport = NetHttpTransport()
        private val jsonFactory: JsonFactory = GsonFactory()
        private var lastCredentials: Credential? = null
        private val authMutex = Mutex()

        fun getTokens(config: OAuthConfig): Credential? {
            return lastCredentials?.takeIf { !it.isExpired() }
        }

        fun getAndMaybeRefreshTokens(config: OAuthConfig): Credential? {
            return lastCredentials?.refreshIfExpired()
        }

        suspend fun refreshTokensOrReauthorize(config: OAuthConfig): Credential? {
            return lastCredentials?.alwaysRefresh() ?: authorize(config)
        }

        suspend fun authorize(config: OAuthConfig): Credential? {
            lastCredentials = null
            return withContext(Dispatchers.IO) {
                authMutex.withLock {
                    lastCredentials?.let { return@withLock it }
                    val flow = AuthorizationCodeFlow.Builder(
                        BearerToken.authorizationHeaderAccessMethod(),
                        httpTransport,
                        jsonFactory,
                        GenericUrl(config.tokenUrl),
                        ClientParametersAuthentication(config.clientId, config.clientSecret),
                        config.clientId,
                        config.authorizationUrl,
                    )
                        .setScopes(config.scopes)
                        .enablePKCE()
                        .build()

                    repeat(100) { n ->
                        val port = 26815 + n
                        try {
                            val receiver: LocalServerReceiver = LocalServerReceiver.Builder().setHost("localhost").setPort(port).build()
                            val tokens = cancelable({ receiver.stop() }) {
                                val scope = this
                                val browser = config.authRequestHandler?.let {
                                    AuthorizationCodeInstalledApp.Browser { url ->
                                        it.browse(object : IAuthRequest {
                                            override fun getUrl(): String = url
                                            override fun cancel() = scope.cancel()
                                            override fun isActive() = scope.isActive
                                        })
                                    }
                                } ?: AuthorizationCodeInstalledApp.DefaultBrowser()
                                AuthorizationCodeInstalledApp(flow, receiver, browser).authorize(null)
                            }
                            lastCredentials = tokens
                            return@withContext tokens
                        } catch (ex: SocketException) {
                            LOG.info("Port $port already in use. Trying next one.")
                            LOG.debug("Login failed with socket exception, which is expected, if we can not open the callback port.", ex)
                        }
                    }
                    throw IllegalStateException("Couldn't find an available port for the redirect URL")
                }
            }
        }

        private fun Credential.isExpired(): Boolean {
            return (expiresInSeconds ?: return false) < 60
        }

        private fun Credential.refreshIfExpired(): Credential? {
            return if (isExpired()) {
                alwaysRefresh()
            } else {
                this.takeIf { it.accessToken != null }
            }
        }

        private fun Credential.alwaysRefresh(): Credential? {
            for (attempt in 1..3) {
                try {
                    val success = refreshToken()
                    if (success) return this
                } catch (e: SocketTimeoutException) {
                    LOG.warn(e) { "Token refresh timed out" }
                } catch (e: TokenResponseException) {
                    LOG.warn("Could not refresh the access token: ${e.details}")
                    break
                }
            }
            return null
        }

        /**
         * [blockingCall] is expected to be uninterruptible by typical platform mechanisms, but by some special call done
         * by [onCancel].
         */
        private suspend fun <R> cancelable(onCancel: suspend () -> Unit, blockingCall: CoroutineScope.() -> R): R {
            return coroutineScope {
                var cancellationEx: CancellationException? = null
                var blockingCallReturned = false
                val cancellationHandlerJob = launch(Dispatchers.IO) {
                    try {
                        awaitCancellation()
                    } catch (ex: CancellationException) {
                        if (!blockingCallReturned) {
                            cancellationEx = ex
                            onCancel()
                        }
                    }
                }
                withContext(Dispatchers.IO) {
                    try {
                        return@withContext blockingCall()
                    } catch (ex: Throwable) {
                        throw cancellationEx ?: ex
                    } finally {
                        blockingCallReturned = true
                        cancellationHandlerJob.cancel()
                    }
                }
            }
        }
    }

    private val cachedTokens: MutableMap<TokenCacheKey, CachedTokens> = Collections.synchronizedMap(HashMap<TokenCacheKey, CachedTokens>())

    private fun getCachedTokens(config: OAuthConfig) = runSynchronized(cachedTokens) {
        cachedTokens.getOrPut(config.getCacheKey()) { CachedTokens() }
    }

    fun getTokens(config: OAuthConfig): Credential? {
        return getCachedTokens(config).getTokens(config)
    }

    fun getAndMaybeRefreshTokens(config: OAuthConfig): Credential? {
        return getCachedTokens(config).getAndMaybeRefreshTokens(config)
    }

    suspend fun refreshTokensOrReauthorize(config: OAuthConfig): Credential? {
        return getCachedTokens(config).refreshTokensOrReauthorize(config)
    }

    suspend fun authorize(config: OAuthConfig): Credential? {
        return getCachedTokens(config).authorize(config)
    }

    @Suppress("UndocumentedPublicFunction") // already documented in the expected declaration
    actual fun installAuth(
        config: HttpClientConfig<*>,
        authConfig: IAuthConfig,
    ) {
        when (authConfig) {
            is TokenProviderAuthConfig -> installAuthWithAuthTokenProvider(config, authConfig)
            is OAuthConfig -> installAuthWithPKCEFlow(config, authConfig)
        }
    }

    private fun installAuthWithPKCEFlow(
        config: HttpClientConfig<*>,
        initialAuthConfig: OAuthConfig,
    ) {
        var currentAuthConfig = initialAuthConfig

        fun String.fillParameters(): String {
            val tokenParameters = currentAuthConfig.tokenParameters ?: return this
            var result = this
            if (result.contains("{repositoryId}")) {
                result = result.replace("{repositoryId}", tokenParameters.getRepositoryId().orEmpty().urlEncode())
            }
            if (result.contains("{branchName}")) {
                result = result.replace("{branchName}", tokenParameters.getBranchName().orEmpty().urlEncode())
            }
            return result
        }

        fun OAuthConfig.fillParameters(): OAuthConfig {
            return copy(tokenUrl = tokenUrl?.fillParameters(), authorizationUrl = authorizationUrl?.fillParameters())
        }

        config.apply {
            install(Auth) {
                bearer {
                    loadTokens {
                        // A potentially expired token is already refreshed here to avoid a 401 response.
                        // When a 401 response is received, we always (re-)execute the PKCE flow.
                        getAndMaybeRefreshTokens(currentAuthConfig)?.let { BearerTokens(it.accessToken, it.refreshToken) }
                    }
                    refreshTokens {
                        try {
                            response.parseWWWAuthenticate()?.let { wwwAuthenticate ->
                                // The model server tells the client where to get a token

                                if (wwwAuthenticate.parameter("error") != "invalid_token") return@let null
                                currentAuthConfig = currentAuthConfig.copy(
                                    authorizationUrl = initialAuthConfig.authorizationUrl
                                        ?: useSameProtocol(wwwAuthenticate.parameter("authorization_uri") ?: return@let null).fillParameters(),
                                    tokenUrl = initialAuthConfig.tokenUrl
                                        ?: useSameProtocol(wwwAuthenticate.parameter("token_uri") ?: return@let null).fillParameters(),
                                )
                                val realm = wwwAuthenticate.parameter("realm")
                                val description = wwwAuthenticate.parameter("error_description")
                            }
                            if (currentAuthConfig.tokenUrl == null) {
                                LOG.warn { "No token URL configured" }
                                return@refreshTokens null
                            }
                            if (currentAuthConfig.authorizationUrl == null) {
                                LOG.warn { "No authorization URL configured" }
                                return@refreshTokens null
                            }
                            if (currentAuthConfig.clientId == null) {
                                LOG.warn { "No client ID configured" }
                                return@refreshTokens null
                            }
                            val tokens = refreshTokensOrReauthorize(currentAuthConfig.fillParameters())
                            checkNotNull(tokens) { "No tokens received" }

                            LOG.info("Access Token: " + tokens.accessToken)

                            BearerTokens(tokens.accessToken, tokens.refreshToken)
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
