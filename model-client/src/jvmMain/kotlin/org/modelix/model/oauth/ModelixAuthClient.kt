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
import kotlinx.coroutines.CoroutineDispatcher
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
import java.util.concurrent.ConcurrentHashMap

@Suppress("UndocumentedPublicClass") // already documented in the expected declaration
actual class ModelixAuthClient {
    companion object {
        private val LOG = mu.KotlinLogging.logger { }

        // Shared across all ModelixAuthClient instances so that different ModelClientV2
        // instances pointed at different branch-specific tokenUrls can reuse one refresh token
        // obtained via a single PKCE login (T031/T032).
        private val cachedTokens: MutableMap<TokenCacheKey, CachedTokens> =
            Collections.synchronizedMap(HashMap<TokenCacheKey, CachedTokens>())
    }

    // One entry per (issuer, clientId): a single refresh token shared across all branches, plus a
    // per-branch (keyed by token URL) cache of the last minted access-token credential. The refresh
    // grant is always issued to the caller's tokenUrl, so each branch gets its own scoped access
    // token from one shared refresh token (T031); those access tokens are cached and reused until
    // near expiry to avoid re-minting on every request (FR-033 skew margin).
    private class CachedTokens(private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO) {
        @Volatile private var storedRefreshToken: String? = null
        private val accessTokensByTokenUrl = ConcurrentHashMap<String, Credential>()
        private val authMutex = Mutex()
        private val httpTransport: HttpTransport = NetHttpTransport()
        private val jsonFactory: JsonFactory = GsonFactory()

        companion object {
            private const val MAX_PORT_ATTEMPTS = 100
            private const val LOCAL_RECEIVER_PORT_BASE = 26815

            // Refresh a little before the access token actually expires to avoid edge-of-expiry
            // races (FR-033).
            private const val EXPIRY_SKEW_SECONDS = 60L
        }

        /** The shared refresh token for this `(issuer, clientId)`, or null if not logged in yet. */
        fun getStoredRefreshToken(): String? = storedRefreshToken

        /** A still-valid cached access token for [config]'s branch token URL, or null. No I/O. */
        fun getCachedAccessToken(config: OAuthConfig): String? =
            config.tokenUrl?.let { accessTokensByTokenUrl[it] }?.takeIf { it.isFresh() }?.accessToken

        suspend fun getAndMaybeRefreshTokens(config: OAuthConfig): Credential? {
            val tokenUrl = config.tokenUrl ?: return null
            // Fast path: reuse a still-valid access token for this branch without locking or I/O.
            accessTokensByTokenUrl[tokenUrl]?.takeIf { it.isFresh() }?.let { return it }
            storedRefreshToken ?: return null
            return authMutex.withLock {
                // Re-check under the lock: another waiter may have just refreshed this branch.
                accessTokensByTokenUrl[tokenUrl]?.takeIf { it.isFresh() }?.let { return@withLock it }
                storedRefreshToken?.let { refreshWithStoredToken(config) }
            }
        }

        suspend fun refreshTokensOrReauthorize(config: OAuthConfig): Credential? {
            return authMutex.withLock {
                refreshWithStoredToken(config) ?: performPKCE(config)
            }
        }

        suspend fun authorize(config: OAuthConfig): Credential? {
            return authMutex.withLock {
                storedRefreshToken?.let { refreshWithStoredToken(config) } ?: performPKCE(config)
            }
        }

        // Performs a refresh_token grant to config.tokenUrl using the stored RT.
        // On invalid_grant the stored RT is discarded (T033).
        // Must be called with authMutex held.
        private suspend fun refreshWithStoredToken(config: OAuthConfig): Credential? {
            val rt = storedRefreshToken ?: return null
            val tokenUrl = config.tokenUrl ?: return null
            return withContext(ioDispatcher) {
                try {
                    val credential = Credential.Builder(BearerToken.authorizationHeaderAccessMethod())
                        .setTransport(httpTransport)
                        .setJsonFactory(jsonFactory)
                        .setTokenServerUrl(GenericUrl(tokenUrl))
                        .setClientAuthentication(ClientParametersAuthentication(config.clientId, config.clientSecret))
                        .build()
                    credential.refreshToken = rt
                    val success = credential.refreshToken()
                    if (success) {
                        // Chain to the latest rotated refresh token (FR-032) and cache the
                        // branch-scoped access token for reuse until near expiry.
                        storedRefreshToken = credential.refreshToken ?: rt
                        accessTokensByTokenUrl[tokenUrl] = credential
                        credential
                    } else {
                        // Anomalous (the refresh token field was set): keep the refresh token but
                        // surface the failure so the caller can fall back to interactive login.
                        LOG.warn("Token refresh returned no credential")
                        null
                    }
                } catch (e: TokenResponseException) {
                    // The refresh token is revoked/expired/reuse-detected: discard it and the
                    // access tokens minted from it, forcing a fresh interactive login (FR-033/036).
                    if (e.details?.error == "invalid_grant") {
                        storedRefreshToken = null
                        accessTokensByTokenUrl.clear()
                    }
                    LOG.warn("Token refresh failed: ${e.details?.error}")
                    null
                } catch (e: SocketTimeoutException) {
                    LOG.warn(e) { "Token refresh timed out" }
                    null
                }
            }
        }

        // Runs the PKCE authorization_code flow. Stores the resulting refresh token.
        // Must be called with authMutex held — this is deliberate: it serializes concurrent branch
        // opens onto a single interactive login ("login once"). The consequence is that while the
        // user is completing the browser login, other branches block on the mutex until this holder
        // finishes or is cancelled; do not narrow the lock scope or two logins can run at once.
        private suspend fun performPKCE(config: OAuthConfig): Credential? {
            storedRefreshToken = null
            accessTokensByTokenUrl.clear()
            return withContext(ioDispatcher) {
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

                repeat(MAX_PORT_ATTEMPTS) { n ->
                    val port = LOCAL_RECEIVER_PORT_BASE + n
                    try {
                        val receiver = LocalServerReceiver.Builder().setHost("localhost").setPort(port).build()
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
                        storedRefreshToken = tokens.refreshToken
                        config.tokenUrl?.let { accessTokensByTokenUrl[it] = tokens }
                        return@withContext tokens
                    } catch (ex: SocketException) {
                        LOG.info("Port $port already in use. Trying next one.")
                        LOG.debug("Login failed with socket exception, which is expected, if we can not open the callback port.", ex)
                    }
                }
                error("Couldn't find an available port for the redirect URL")
            }
        }

        /**
         * Whether this credential has a usable access token that is not within [EXPIRY_SKEW_SECONDS]
         * of expiry. A credential with no expiry information is assumed usable (its staleness is
         * still caught reactively by the 401 -> refresh path).
         */
        private fun Credential.isFresh(): Boolean {
            accessToken ?: return false
            val expiresIn = expiresInSeconds ?: return true
            return expiresIn > EXPIRY_SKEW_SECONDS
        }

        /**
         * [blockingCall] is expected to be uninterruptible by typical platform mechanisms, but by some special call done
         * by [onCancel].
         */
        private suspend fun <R> cancelable(onCancel: suspend () -> Unit, blockingCall: CoroutineScope.() -> R): R {
            return coroutineScope {
                var cancellationEx: CancellationException? = null
                var blockingCallReturned = false
                val cancellationHandlerJob = launch(ioDispatcher) {
                    try {
                        awaitCancellation()
                    } catch (ex: CancellationException) {
                        if (!blockingCallReturned) {
                            cancellationEx = ex
                            onCancel()
                        }
                    }
                }
                withContext(ioDispatcher) {
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

    private fun getCachedTokens(config: OAuthConfig) = runSynchronized(cachedTokens) {
        cachedTokens.getOrPut(config.getCacheKey()) { CachedTokens() }
    }

    /**
     * Returns the OAuth refresh token cached in this JVM for the authorization server identified by
     * [config] (its `(issuer/authorizationUrl, clientId, clientSecret, scopes)`), or `null` if no
     * interactive login has happened yet in this process.
     *
     * Java-friendly accessor: it does not suspend and performs no network I/O — it only reads the
     * in-memory cache that every [ModelixAuthClient] in the JVM shares, so the result is independent
     * of which instance it is called on. Credentials are never persisted, so a different process
     * always returns `null` until it logs in itself.
     */
    fun getRefreshToken(config: OAuthConfig): String? = getCachedTokens(config).getStoredRefreshToken()

    /**
     * Returns a still-valid access token cached in this JVM for [config]'s branch-specific
     * [OAuthConfig.tokenUrl], or `null` when none is cached or it is within the expiry skew margin.
     *
     * Java-friendly accessor: it does not suspend and performs no network I/O. When no token is
     * cached, drive a request through the authenticated client (or call [getRefreshToken]) — the
     * normal request flow runs the refresh/login and populates this cache.
     */
    fun getAccessToken(config: OAuthConfig): String? = getCachedTokens(config).getCachedAccessToken(config)

    private suspend fun getAndMaybeRefreshTokens(config: OAuthConfig): Credential? {
        return getCachedTokens(config).getAndMaybeRefreshTokens(config)
    }

    private suspend fun refreshTokensOrReauthorize(config: OAuthConfig): Credential? {
        return getCachedTokens(config).refreshTokensOrReauthorize(config)
    }

    // Visible for ModelixAuthClientTest (cancellation behavior); not part of the public API.
    internal suspend fun authorize(config: OAuthConfig): Credential? {
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
                        // Use fillParameters() so the branch-specific tokenUrl is used for the
                        // refresh grant even before any 401 is received (T031).
                        getAndMaybeRefreshTokens(currentAuthConfig.fillParameters())?.let { BearerTokens(it.accessToken, it.refreshToken) }
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

    /**
     * Parses the `WWW-Authenticate` challenge from this message, falling back to the non-standard
     * `x-amzn-remapped-www-authenticate` header that the Amazon API Gateway substitutes to suppress
     * browser login popups. Returns `null` when no parameterized challenge is present.
     */
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
