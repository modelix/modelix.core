package org.modelix.model.oauth

import org.modelix.model.lazy.RepositoryId

sealed interface IAuthConfig {
    companion object {
        fun fromTokenProvider(provider: TokenProvider): IAuthConfig {
            return TokenProviderAuthConfig(provider)
        }

        fun oauth(body: OAuthConfigBuilder.() -> Unit): IAuthConfig {
            return OAuthConfigBuilder(null).apply(body).build()
        }
    }
}

class TokenProviderAuthConfig(val provider: TokenProvider) : IAuthConfig

data class OAuthConfig(
    val clientId: String? = "external-mps",
    val clientSecret: String? = null,
    val authorizationUrl: String? = null,
    val tokenUrl: String? = null,
    val repositoryId: RepositoryId? = null,
    val scopes: Set<String> = emptySet(),
    val authRequestHandler: IAuthRequestHandler? = null,
) : IAuthConfig

typealias TokenProvider = suspend () -> String?

class OAuthConfigBuilder(initial: OAuthConfig?) {
    private var config = initial ?: OAuthConfig()

    fun clientId(id: String) = also { config = config.copy(clientId = id) }
    fun clientSecret(secret: String) = also { config = config.copy(clientSecret = secret) }
    fun scopes(scopes: Iterable<String>) = also { config = config.copy(scopes = scopes.toSet()) }
    fun scope(scope: String) = scopes(setOf(scope))
    fun additionalScopes(scopes: Iterable<String>) = also { config = config.copy(scopes = config.scopes + scopes) }
    fun additionalScope(scope: String) = additionalScopes(setOf(scope))
    fun authorizationUrl(url: String) = also { config = config.copy(authorizationUrl = url) }
    fun tokenUrl(url: String) = also { config = config.copy(tokenUrl = url) }
    fun repositoryId(repositoryId: RepositoryId) = also { config = config.copy(repositoryId = repositoryId) }
    fun oidcUrl(url: String) = authorizationUrl(url.trimEnd('/') + "/auth").tokenUrl(url.trimEnd('/') + "/token")
    fun authRequestHandler(handler: IAuthRequestHandler?) = also { config = config.copy(authRequestHandler = handler) }

    fun build() = config.copy()
}

interface IAuthRequestHandler {
    /**
     * Open the URL where the user can log in. The OAuth server will redirect the user to a callback URL to transmit
     * the authorization code.
     */
    fun browse(url: String)
}
