package org.modelix.authorization

import com.auth0.jwt.interfaces.DecodedJWT
import com.nimbusds.jose.JWSAlgorithm
import com.nimbusds.jose.jwk.JWK
import io.ktor.server.application.Application
import io.ktor.server.application.plugin
import org.modelix.authorization.permissions.Schema
import org.modelix.authorization.permissions.buildPermissionSchema
import java.io.File
import java.net.URI
import java.security.MessageDigest

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
     * This key is made available at /.well-known/jwks.json so that other services can verify that a token was created
     * by this server.
     */
    var ownPublicKey: JWK?

    /**
     * In addition to JWKS URLs you can directly provide keys for verification of tokens sent in requests to
     * this server.
     */
    fun addForeignPublicKey(key: JWK)

    /**
     * If RSA signatures a used, the public key will be downloaded from this registry.
     */
    var jwkUri: URI?

    /**
     * The ID of the public key for the RSA signature.
     */
    @Deprecated("The key ID is supposed to be retrieved from the token")
    var jwkKeyId: String?

    /**
     * Defines the available permissions and their relations.
     */
    var permissionSchema: Schema

    var accessControlDataProvider: IAccessControlDataProvider

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
    override var ownPublicKey: JWK? = null
    private val foreignPublicKeys = ArrayList<JWK>()
    override var jwkUri: URI? = System.getenv("MODELIX_JWK_URI")?.let { URI(it) }
        ?: System.getenv("KEYCLOAK_BASE_URL")?.let { keycloakBaseUrl ->
            System.getenv("KEYCLOAK_REALM")?.let { keycloakRealm ->
                URI("${keycloakBaseUrl}realms/$keycloakRealm/protocol/openid-connect/certs")
            }
        }
    override var jwkKeyId: String? = System.getenv("MODELIX_JWK_KEY_ID")
    override var permissionSchema: Schema = buildPermissionSchema { }
    override var accessControlDataProvider: IAccessControlDataProvider = EmptyAccessControlDataProvider()

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

    val jwtUtil: ModelixJWTUtil by lazy {
        val util = ModelixJWTUtil()

        util.accessControlDataProvider = accessControlDataProvider
        util.loadKeysFromEnvironment()

        listOfNotNull<Pair<String, JWSAlgorithm>>(
            hmac512Key?.let { it to JWSAlgorithm.HS512 },
            hmac384Key?.let { it to JWSAlgorithm.HS384 },
            hmac256Key?.let { it to JWSAlgorithm.HS256 },
            hmac512KeyFromEnv?.let { it to JWSAlgorithm.HS512 },
            hmac384KeyFromEnv?.let { it to JWSAlgorithm.HS384 },
            hmac256KeyFromEnv?.let { it to JWSAlgorithm.HS256 },
        ).forEach { util.addHmacKey(it.first, it.second) }

        jwkUri?.let { util.addJwksUrl(it.toURL()) }

        // allows multiple URLs (MODELIX_JWK_URI1, MODELIX_JWK_URI2, MODELIX_JWK_URI_MODEL_SERVER, ...)
        System.getenv().filter { it.key.startsWith("MODELIX_JWK_URI") }.values
            .forEach { util.addJwksUrl(URI(it).toURL()) }

        foreignPublicKeys.forEach { util.addPublicKey(it) }

        jwkKeyId?.let { util.requireKeyId(it) }
        util
    }

    override fun addForeignPublicKey(key: JWK) {
        foreignPublicKeys.add(key)
    }

    fun verifyTokenSignature(token: DecodedJWT) {
        jwtUtil.verifyToken(token.token) // will throw an exception if it's invalid
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
    fun shouldGenerateFakeTokens() = generateFakeTokens ?: !jwtUtil.canVerifyTokens()

    /**
     * Whether permission checking should be enabled based on the configuration values provided.
     */
    fun permissionCheckingEnabled() = permissionChecksEnabled ?: jwtUtil.canVerifyTokens()

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

internal fun ByteArray.repeatBytes(minimumSize: Int): ByteArray {
    if (size >= minimumSize) return this
    val repeated = ByteArray(((size / 256) + 1) * 256)
    for (i in repeated.indices) repeated[i] = this[i % size]
    return repeated
}

fun ByteArray.ensureMinSecretLength(algorithm: JWSAlgorithm): ByteArray {
    val secret = this
    when (algorithm) {
        JWSAlgorithm.HS512 -> {
            if (secret.size < 512) {
                val digest = MessageDigest.getInstance("SHA-512")
                digest.update(secret)
                return digest.digest()
            }
        }
        JWSAlgorithm.HS384 -> {
            if (secret.size < 384) {
                val digest = MessageDigest.getInstance("SHA-384")
                digest.update(secret)
                return digest.digest()
            }
        }
        JWSAlgorithm.HS256 -> {
            if (secret.size < 256) {
                val digest = MessageDigest.getInstance("SHA-256")
                digest.update(secret)
                return digest.digest()
            }
        }
    }
    return secret
}
