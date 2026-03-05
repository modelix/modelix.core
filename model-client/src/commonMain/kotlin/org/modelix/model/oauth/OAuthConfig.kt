package org.modelix.model.oauth

import org.modelix.model.lazy.BranchReference
import org.modelix.model.lazy.RepositoryId

sealed interface IAuthConfig {
    companion object {
        @Deprecated("Provide an ITokenProvider")
        fun fromTokenProvider(provider: TokenProvider): IAuthConfig {
            return fromTokenProvider(TokenProviderAdapter(provider))
        }

        fun fromTokenProvider(provider: ITokenProvider): IAuthConfig {
            return TokenProviderAuthConfig(provider)
        }

        fun oauth(body: OAuthConfigBuilder.() -> Unit): IAuthConfig {
            return OAuthConfigBuilder(null).apply(body).build()
        }
    }

    fun withTokenParameters(parameters: ITokenParameters): IAuthConfig
}

class TokenProviderAuthConfig(
    val provider: ITokenProvider,
    val tokenParameters: ITokenParameters = GlobalTokenParameters(),
) : IAuthConfig {
    override fun withTokenParameters(parameters: ITokenParameters): IAuthConfig {
        return TokenProviderAuthConfig(provider, parameters)
    }
}

data class OAuthConfig(
    val clientId: String? = "external-mps",
    val clientSecret: String? = null,
    val authorizationUrl: String? = null,
    val tokenUrl: String? = null,
    val tokenParameters: ITokenParameters? = null,
    val scopes: Set<String> = emptySet(),
    val authRequestHandler: IAuthRequestHandler? = null,
) : IAuthConfig {
    fun getCacheKey() = TokenCacheKey(
        clientId = clientId,
        clientSecret = clientSecret,
        authorizationUrl = authorizationUrl,
        tokenUrl = tokenUrl,
        scopes = scopes,
    )

    override fun withTokenParameters(parameters: ITokenParameters): IAuthConfig {
        return copy(tokenParameters = parameters)
    }
}

data class TokenCacheKey(
    val clientId: String?,
    val clientSecret: String?,
    val authorizationUrl: String?,
    val tokenUrl: String?,
    val scopes: Set<String>,
)

@Deprecated("use ITokenProvider")
typealias TokenProvider = suspend () -> String?

interface ITokenProvider {
    suspend fun getToken(): String? = null
    suspend fun getToken(parameters: ITokenParameters): String? = getToken()
}

class TokenProviderAdapter(val provider: suspend () -> String?) : ITokenProvider {
    override suspend fun getToken(): String? = provider()
}

interface ITokenParameters {
    fun getRepositoryId(): String?
    fun getBranchName(): String?
}

class TokenParameters(private val repositoryId: RepositoryId?, private val branchName: String?) : ITokenParameters {
    constructor(repositoryId: RepositoryId) : this(repositoryId, null)
    constructor(branchReference: BranchReference) : this(branchReference.repositoryId, branchReference.branchName)

    private var dependsOnRepositoryId: Boolean = false
    private var dependsOnBranchName: Boolean = false

    override fun getRepositoryId(): String? {
        dependsOnRepositoryId = true
        return repositoryId?.id
    }

    override fun getBranchName(): String? {
        dependsOnBranchName = true
        return branchName
    }

    fun createCacheKey(): Any {
        return listOf(
            repositoryId?.id?.takeIf { dependsOnRepositoryId },
            branchName?.takeIf { dependsOnBranchName },
        )
    }
}

class GlobalTokenParameters : ITokenParameters {
    override fun getRepositoryId(): String? = null
    override fun getBranchName(): String? = null
}

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
    fun repositoryId(repositoryId: RepositoryId) = tokenParameters(
        TokenParameters(
            repositoryId = repositoryId,
            branchName = null,
        ),
    )
    fun tokenParameters(parameters: ITokenParameters) = also { config = config.copy(tokenParameters = parameters) }
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
