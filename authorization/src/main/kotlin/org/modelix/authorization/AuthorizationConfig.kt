package org.modelix.authorization

import com.auth0.jwk.JwkProvider
import com.auth0.jwk.JwkProviderBuilder
import com.auth0.jwt.JWT
import com.auth0.jwt.JWTVerifier
import com.auth0.jwt.algorithms.Algorithm
import com.auth0.jwt.interfaces.DecodedJWT
import io.ktor.server.application.Application
import io.ktor.server.application.plugin
import org.modelix.authorization.permissions.Schema
import org.modelix.authorization.permissions.buildPermissionSchema
import java.io.File
import java.net.URI
import java.security.interfaces.RSAPublicKey

private val LOG = mu.KotlinLogging.logger { }

/**
 * Reduced interface exposed to users of the plugin.
 */
interface IModelixAuthorizationConfig {
    /**
     * A JWT principal will be available, even if the HTTP request doesn't contain one.
     */
    var generateFakeTokens: Boolean?

    /**
     * If not explicitly enabled or disabled, permissions are check if an algorithm for the JWT signature is configured.
     */
    var permissionChecksEnabled: Boolean?

    /**
     * /user will show the content of the JWT token
     * /permissions will show all available permissions that can be used when generating a token.
     */
    var debugEndpointsEnabled: Boolean

    /**
     * The pre-shared key for the HMAC512 signature algorithm.
     * The environment variables MODELIX_JWT_SIGNATURE_HMAC512_KEY or MODELIX_JWT_SIGNATURE_HMAC512_KEY_FILE can be
     * used instead.
     */
    var hmac512Key: String?

    /**
     * The pre-shared key for the HMAC384 signature algorithm.
     * The environment variables MODELIX_JWT_SIGNATURE_HMAC384_KEY or MODELIX_JWT_SIGNATURE_HMAC384_KEY_FILE can be
     * used instead.
     */
    var hmac384Key: String?

    /**
     * The pre-shared key for the HMAC256 signature algorithm.
     * The environment variables MODELIX_JWT_SIGNATURE_HMAC256_KEY or MODELIX_JWT_SIGNATURE_HMAC256_KEY_FILE can be
     * used instead.
     */
    var hmac256Key: String?

    /**
     * If RSA signatures a used, the public key will be downloaded from this registry.
     */
    var jwkUri: URI?

    /**
     * The ID of the public key for the RSA signature.
     */
    var jwkKeyId: String?

    /**
     * Defines the available permissions and their relations.
     */
    var permissionSchema: Schema

    /**
     * Generates fake tokens and allows all requests.
     */
    fun configureForUnitTests()
}

class ModelixAuthorizationConfig : IModelixAuthorizationConfig {
    override var permissionChecksEnabled: Boolean? = PERMISSION_CHECKS_ENABLED
    override var generateFakeTokens: Boolean? = getBooleanFromEnv("MODELIX_GENERATE_FAKE_JWT")
    override var debugEndpointsEnabled: Boolean = true
    override var hmac512Key: String? = null
    override var hmac384Key: String? = null
    override var hmac256Key: String? = null
    override var jwkUri: URI? = System.getenv("MODELIX_JWK_URI")?.let { URI(it) }
        ?: System.getenv("KEYCLOAK_BASE_URL")?.let { keycloakBaseUrl ->
            System.getenv("KEYCLOAK_REALM")?.let { keycloakRealm ->
                URI("${keycloakBaseUrl}realms/$keycloakRealm/protocol/openid-connect/certs")
            }
        }
    override var jwkKeyId: String? = System.getenv("MODELIX_JWK_KEY_ID")
    override var permissionSchema: Schema = buildPermissionSchema { }

    private val hmac512KeyFromEnv by lazy {
        System.getenv("MODELIX_JWT_SIGNATURE_HMAC512_KEY")
            ?: System.getenv("MODELIX_JWT_SIGNATURE_HMAC512_KEY_FILE")?.let { File(it).readText() }
    }
    private val hmac384KeyFromEnv by lazy {
        System.getenv("MODELIX_JWT_SIGNATURE_HMAC384_KEY")
            ?: System.getenv("MODELIX_JWT_SIGNATURE_HMAC384_KEY_FILE")?.let { File(it).readText() }
    }
    private val hmac256KeyFromEnv by lazy {
        System.getenv("MODELIX_JWT_SIGNATURE_HMAC256_KEY")
            ?: System.getenv("MODELIX_JWT_SIGNATURE_HMAC256_KEY_FILE")?.let { File(it).readText() }
    }

    private val cachedJwkProvider: JwkProvider? by lazy {
        jwkUri?.let { JwkProviderBuilder(it.toURL()).build() }
    }

    private val algorithm: Algorithm? by lazy {
        hmac512Key?.let { return@lazy Algorithm.HMAC512(it) }
        hmac384Key?.let { return@lazy Algorithm.HMAC384(it) }
        hmac256Key?.let { return@lazy Algorithm.HMAC256(it) }
        hmac512KeyFromEnv?.let { return@lazy Algorithm.HMAC512(it) }
        hmac384KeyFromEnv?.let { return@lazy Algorithm.HMAC384(it) }
        hmac256KeyFromEnv?.let { return@lazy Algorithm.HMAC256(it) }

        val localJwkProvider = cachedJwkProvider
        val localJwkKeyId = jwkKeyId
        if (localJwkProvider == null || localJwkKeyId == null) {
            return@lazy null
        }
        return@lazy getAlgorithmFromJwkProviderAndKeyId(localJwkProvider, localJwkKeyId)
    }

    private fun getAlgorithmFromJwkProviderAndKeyId(jwkProvider: JwkProvider, jwkKeyId: String): Algorithm {
        val jwk = jwkProvider.get(jwkKeyId)
        val publicKey = jwk.publicKey as? RSAPublicKey ?: error("Invalid key type: ${jwk.publicKey}")
        return when (jwk.algorithm) {
            "RS256" -> Algorithm.RSA256(publicKey, null)
            "RSA384" -> Algorithm.RSA384(publicKey, null)
            "RS512" -> Algorithm.RSA512(publicKey, null)
            else -> error("Unsupported algorithm: ${jwk.algorithm}")
        }
    }

    fun getJwtSignatureAlgorithmOrNull(): Algorithm? {
        return algorithm
    }

    fun getJwkProvider(): JwkProvider? {
        return cachedJwkProvider
    }

    fun verifyTokenSignature(token: DecodedJWT) {
        val algorithm = getJwtSignatureAlgorithmOrNull()
        val jwkProvider = getJwkProvider()

        val verifier = if (algorithm != null) {
            getVerifierForSpecificAlgorithm(algorithm)
        } else if (jwkProvider != null) {
            val algorithmForKeyFromToken = getAlgorithmFromJwkProviderAndKeyId(jwkProvider, token.keyId)
            getVerifierForSpecificAlgorithm(algorithmForKeyFromToken)
        } else {
            error("Either an JWT algorithm or a JWK URI must be configured.")
        }
        verifier.verify(token)
    }

    fun nullIfInvalid(token: DecodedJWT): DecodedJWT? {
        return try {
            verifyTokenSignature(token)
            token
        } catch (e: Exception) {
            LOG.warn(e) { "Invalid JWT token: ${token.token}" }
            null
        }
    }

    // TODO MODELIX-1019 Instead of creating a fake token, we should refactor our code to work without a username
    // when no authentication and authorization is configured.
    /**
     * Whether a fake token should be generated based on the configuration values provided.
     *
     * The fake token is generated so that we always have a username that can be used in the server logic.
     */
    fun shouldGenerateFakeTokens() = generateFakeTokens ?: (algorithm == null && cachedJwkProvider == null)

    /**
     * Whether permission checking should be enabled based on the configuration values provided.
     */
    fun permissionCheckingEnabled() = permissionChecksEnabled ?: (algorithm != null || cachedJwkProvider != null)

    override fun configureForUnitTests() {
        generateFakeTokens = true
        permissionChecksEnabled = false
    }

    companion object {
        val PERMISSION_CHECKS_ENABLED = getBooleanFromEnv("MODELIX_PERMISSION_CHECKS_ENABLED")
    }
}

fun Application.getModelixAuthorizationConfig(): ModelixAuthorizationConfig {
    return plugin(ModelixAuthorization).config
}

private fun getBooleanFromEnv(name: String): Boolean? {
    try {
        return System.getenv(name)?.toBooleanStrict()
    } catch (ex: IllegalArgumentException) {
        throw IllegalArgumentException("Failed to read boolean value $name", ex)
    }
}

internal fun getVerifierForSpecificAlgorithm(algorithm: Algorithm): JWTVerifier =
    JWT.require(algorithm)
        .build()
