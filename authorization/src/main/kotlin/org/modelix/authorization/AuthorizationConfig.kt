/*
 * Copyright (c) 2024.
 *
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

package org.modelix.authorization

import com.auth0.jwk.JwkProvider
import com.auth0.jwk.JwkProviderBuilder
import com.auth0.jwt.JWT
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
    override var permissionChecksEnabled: Boolean? = getBooleanFromEnv("MODELIX_PERMISSION_CHECKS_ENABLED")
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

        val jwk = cachedJwkProvider?.get(jwkKeyId)
        if (jwk != null) {
            val publicKey = jwk.publicKey as? RSAPublicKey ?: error("Invalid key type: ${jwk.publicKey}")
            return@lazy when (jwk.algorithm) {
                "RS256" -> Algorithm.RSA256(publicKey, null)
                "RSA384" -> Algorithm.RSA384(publicKey, null)
                "RS512" -> Algorithm.RSA512(publicKey, null)
                else -> error("Unsupported algorithm: ${jwk.algorithm}")
            }
        }

        null
    }

    fun getJwtSignatureAlgorithm(): Algorithm {
        return checkNotNull(algorithm) { "No signature algorithm configured" }
    }

    fun getJwtSignatureAlgorithmOrNull(): Algorithm? {
        return algorithm
    }

    fun getJwkProvider(): JwkProvider? {
        return cachedJwkProvider
    }

    fun verifyTokenSignature(token: DecodedJWT) {
        val algorithm = getJwtSignatureAlgorithm()
        val verifier = JWT.require(algorithm)
            .acceptLeeway(0L)
            .build()
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

    fun shouldGenerateFakeTokens() = generateFakeTokens ?: (algorithm == null)
    fun permissionCheckingEnabled() = permissionChecksEnabled ?: (algorithm != null)

    override fun configureForUnitTests() {
        generateFakeTokens = true
        permissionChecksEnabled = false
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
