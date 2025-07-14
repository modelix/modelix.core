package org.modelix.authorization

import com.auth0.jwt.interfaces.Payload
import com.nimbusds.jose.JWSAlgorithm
import com.nimbusds.jose.JWSHeader
import com.nimbusds.jose.JWSObject
import com.nimbusds.jose.JWSSigner
import com.nimbusds.jose.JWSVerifier
import com.nimbusds.jose.KeySourceException
import com.nimbusds.jose.crypto.MACSigner
import com.nimbusds.jose.crypto.RSASSASigner
import com.nimbusds.jose.crypto.factories.DefaultJWSVerifierFactory
import com.nimbusds.jose.jwk.JWK
import com.nimbusds.jose.jwk.JWKMatcher
import com.nimbusds.jose.jwk.JWKSelector
import com.nimbusds.jose.jwk.JWKSet
import com.nimbusds.jose.jwk.KeyType
import com.nimbusds.jose.jwk.KeyUse
import com.nimbusds.jose.jwk.RSAKey
import com.nimbusds.jose.jwk.gen.RSAKeyGenerator
import com.nimbusds.jose.jwk.source.ImmutableJWKSet
import com.nimbusds.jose.jwk.source.JWKSource
import com.nimbusds.jose.jwk.source.RemoteJWKSet
import com.nimbusds.jose.proc.BadJOSEException
import com.nimbusds.jose.proc.JWSAlgorithmFamilyJWSKeySelector
import com.nimbusds.jose.proc.JWSKeySelector
import com.nimbusds.jose.proc.SecurityContext
import com.nimbusds.jose.proc.SingleKeyJWSKeySelector
import com.nimbusds.jose.util.AbstractRestrictedResourceRetriever
import com.nimbusds.jose.util.Resource
import com.nimbusds.jwt.JWTClaimsSet
import com.nimbusds.jwt.JWTParser
import com.nimbusds.jwt.proc.DefaultJWTProcessor
import com.nimbusds.jwt.proc.JWTProcessor
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import io.ktor.http.contentType
import io.ktor.server.auth.jwt.JWTPrincipal
import kotlinx.coroutines.runBlocking
import org.modelix.authorization.permissions.PermissionEvaluator
import org.modelix.authorization.permissions.Schema
import org.modelix.authorization.permissions.SchemaInstance
import java.io.File
import java.net.URI
import java.net.URL
import java.security.Key
import java.security.MessageDigest
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.Base64
import java.util.Date
import java.util.UUID
import javax.crypto.spec.SecretKeySpec
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

class ModelixJWTUtil {
    private val hmacKeys = LinkedHashMap<JWSAlgorithm, ByteArray>()
    private val jwkSources = ArrayList<JWKSource<SecurityContext>>()
    private var expectedKeyId: String? = null
    private var ktorClient: HttpClient? = null
    var accessControlDataProvider: IAccessControlDataProvider = EmptyAccessControlDataProvider()

    private var jwtProcessor: JWTProcessor<SecurityContext>? = null
    var fileRefreshTime: Duration = 5.seconds

    @Synchronized
    private fun getOrCreateJwtProcessor(): JWTProcessor<SecurityContext> {
        return jwtProcessor ?: DefaultJWTProcessor<SecurityContext>().also { processor ->
            val keySelectors: List<JWSKeySelector<SecurityContext>> = hmacKeys.map { it.toPair() }.map {
                SingleKeyJWSKeySelector<SecurityContext>(it.first, SecretKeySpec(it.second, it.first.name))
            } + jwkSources.map {
                try {
                    JWSAlgorithmFamilyJWSKeySelector.fromJWKSource<SecurityContext>(it)
                } catch (ex: KeySourceException) {
                    throw KeySourceException("Couldn't retrieve JWKs from $it", ex)
                }
            }

            processor.jwsKeySelector = if (keySelectors.size == 1) keySelectors.single() else CompositeJWSKeySelector(keySelectors)

            val expectedKeyId = this.expectedKeyId
            if (expectedKeyId != null) {
                processor.jwsVerifierFactory = object : DefaultJWSVerifierFactory() {
                    override fun createJWSVerifier(header: JWSHeader, key: Key): JWSVerifier {
                        if (header.keyID != expectedKeyId) {
                            throw BadJOSEException("Invalid key ID. [expected=$expectedKeyId, actual=${header.keyID}]")
                        }
                        return super.createJWSVerifier(header, key)
                    }
                }
            }
        }.also { jwtProcessor = it }
    }

    fun getPrivateKey(): JWK? {
        return jwkSources.flatMap {
            it.get(JWKSelector(JWKMatcher.Builder().privateOnly(true).algorithms(JWSAlgorithm.Family.RSA.toSet()).build()), null)
        }.firstOrNull()
    }

    private fun resetJwtProcessor() {
        jwtProcessor = null
    }

    @Synchronized
    fun canVerifyTokens(): Boolean {
        return hmacKeys.isNotEmpty() || jwkSources.isNotEmpty()
    }

    /**
     * Tokens are only valid if they are signed with this key.
     */
    @Synchronized
    fun requireKeyId(id: String) {
        expectedKeyId = id
    }

    @Synchronized
    fun useKtorClient(client: HttpClient) {
        resetJwtProcessor()
        this.ktorClient = client.config {
            expectSuccess = true
        }
    }

    @Synchronized
    fun addJwksUrl(url: String) {
        addJwksUrl(URI(url).toURL())
    }

    @Synchronized
    fun addJwksUrl(url: URL) {
        resetJwtProcessor()
        jwkSources.add(RemoteJWKSet(url, ktorClient?.let { KtorResourceRetriever(it) }))
    }

    fun setHmac512Key(key: String) {
        addHmacKey(key, JWSAlgorithm.HS512)
    }

    fun addHmacKey(key: String, algorithm: JWSAlgorithm) {
        // nimbusds checks for weak keys that are shorter than 256 bytes
        addHmacKey(key.toByteArray().ensureMinSecretLength(algorithm), algorithm)
    }

    @Synchronized
    fun addPublicKey(key: JWK) {
        requireNotNull(key.keyID) { "Key doesn't specify a key ID: $key" }
        requireNotNull(key.algorithm) { "Key doesn't specify an algorithm: $key" }
        resetJwtProcessor()
        jwkSources.add(ImmutableJWKSet(JWKSet(key.toPublicJWK())))
    }

    @Synchronized
    fun setRSAPrivateKey(key: JWK) {
        requireNotNull(key.keyID) { "Key doesn't specify a key ID: $key" }
        requireNotNull(key.algorithm) { "Key doesn't specify an algorithm: $key" }
        resetJwtProcessor()
        jwkSources.add(ImmutableJWKSet(JWKSet(listOf(key, key.toPublicJWK()))))
    }

    @Synchronized
    fun addJWK(key: JWK) {
        requireNotNull(key.keyID) { "Key doesn't specify a key ID: $key" }
        requireNotNull(key.algorithm) { "Key doesn't specify an algorithm: $key" }
        resetJwtProcessor()
        if (key.isPrivate) {
            jwkSources.add(ImmutableJWKSet(JWKSet(listOf(key, key.toPublicJWK()))))
        } else {
            jwkSources.add(ImmutableJWKSet(JWKSet(key)))
        }
    }

    @Synchronized
    private fun addHmacKey(key: ByteArray, algorithm: JWSAlgorithm) {
        resetJwtProcessor()
        hmacKeys[algorithm] = key
    }

    @Synchronized
    fun loadKeysFromEnvironment() {
        resetJwtProcessor()
        System.getenv().filter { it.key.startsWith("MODELIX_JWK_FILE") }.values.forEach {
            loadKeysFromFiles(File(it))
        }

        // allows multiple URLs (MODELIX_JWK_URI1, MODELIX_JWK_URI2, MODELIX_JWK_URI_MODEL_SERVER, ...)
        System.getenv().filter { it.key.startsWith("MODELIX_JWK_URI") }.values
            .forEach { addJwksUrl(URI(it).toURL()) }
    }

    fun loadKeysFromFiles(fileOrFolder: File) {
        fileOrFolder.walk().forEach { file ->
            when (file.extension) {
                "pem" -> jwkSources.add(PemFileJWKSet(file))
                "json" -> jwkSources.add(FileJWKSet(file))
            }
        }
    }

    @Synchronized
    fun createAccessToken(user: String, grantedPermissions: List<String>, additionalTokenContent: (TokenBuilder) -> Unit = {}): String {
        val signer: JWSSigner
        val algorithm: JWSAlgorithm
        val signingKeyId: String?
        val jwk = getPrivateKey()
        if (jwk != null) {
            signer = RSASSASigner(jwk.toRSAKey().toRSAPrivateKey())
            algorithm = checkNotNull(jwk.algorithm) { "RSA key doesn't specify an algorithm" }
                .let { it as? JWSAlgorithm ?: JWSAlgorithm.parse(it.name) }
            signingKeyId = checkNotNull(jwk.keyID) { "RSA key doesn't specify a key ID" }
        } else {
            val entry = checkNotNull(hmacKeys.entries.firstOrNull()) { "No keys for signing provided" }
            signer = MACSigner(entry.value)
            algorithm = entry.key
            signingKeyId = null
        }

        val payload = JWTClaimsSet.Builder()
            .claim(KeycloakTokenConstants.PREFERRED_USERNAME, user)
            .claim(ModelixTokenConstants.PERMISSIONS, grantedPermissions)
            .expirationTime(Date(Instant.now().plus(12, ChronoUnit.HOURS).toEpochMilli()))
            .also { additionalTokenContent(TokenBuilder(it)) }
            .build()
            .toPayload()
        val header = JWSHeader.Builder(algorithm).keyID(signingKeyId).build()
        return JWSObject(header, payload).also { it.sign(signer) }.serialize()
    }

    fun isAccessToken(token: Payload): Boolean {
        return extractPermissions(token) != null
    }

    fun isIdentityToken(token: Payload): Boolean {
        return !isAccessToken(token)
    }

    fun createPermissionEvaluator(token: Payload, schema: Schema): PermissionEvaluator {
        return createPermissionEvaluator(token, SchemaInstance(schema))
    }

    fun createPermissionEvaluator(token: Payload, schema: SchemaInstance): PermissionEvaluator {
        return PermissionEvaluator(schema).also { loadGrantedPermissions(token, it) }
    }

    fun extractPermissions(token: Payload): List<String>? {
        return token.claims[ModelixTokenConstants.PERMISSIONS]?.asList(String::class.java)
    }

    @Synchronized
    fun loadGrantedPermissions(token: Payload, evaluator: PermissionEvaluator) {
        val permissions = extractPermissions(token)

        // There is a difference between access tokens and identity tokens.
        // An identity token just contains the user ID and the service has to know the granted permissions.
        // An access token has more limited permissions and is issued for a specific task. It contains the list of
        // granted permissions. Since tokens are signed and created by a trusted authority we don't have to check the
        // list of permissions against our own access control data.
        if (permissions != null) {
            permissions.forEach { evaluator.grantPermission(it) }
        } else {
            val directGrants = extractUserId(token)?.let { userId ->
                accessControlDataProvider.getGrantedPermissionsForUser(userId)
            }.orEmpty() + extractUserRoles(token).flatMap { role ->
                accessControlDataProvider.getGrantedPermissionsForRole(role)
            }.toSet()
            directGrants.forEach { permission ->
                evaluator.grantPermission(permission)
            }
        }
    }

    fun extractUserId(jwt: Payload): String? {
        return Companion.extractUserId(jwt)
    }

    fun extractUserRoles(jwt: Payload): List<String> {
        val keycloakRoles = jwt
            .getClaim(KeycloakTokenConstants.REALM_ACCESS)?.asMap()
            ?.get(KeycloakTokenConstants.REALM_ACCESS_ROLES)
            ?.let { it as? List<*> }
            ?.mapNotNull { it as? String }
            ?: emptyList()
        return keycloakRoles
    }

    fun generateRSAPrivateKey(): JWK {
        return RSAKeyGenerator(2048)
            .keyUse(KeyUse.SIGNATURE)
            .keyID(UUID.randomUUID().toString())
            .issueTime(Date())
            .algorithm(JWSAlgorithm.RS256)
            .generate()
            .also { addJWK(it) }
    }

    private fun ensureValidKey(key: JWK): JWK {
        return ensureKeyId(ensureAlgorithmSet(key))
    }

    private fun ensureAlgorithmSet(key: JWK): JWK {
        if (key.algorithm != null) return key
        require(key.keyType == KeyType.RSA) { "Unsupported key type: ${key.keyType}" }
        return RSAKey.Builder(key.toRSAKey()).algorithm(JWSAlgorithm.RS256).build()
    }

    private fun ensureKeyId(key: JWK): JWK {
        if (key.keyID != null) return key

        val rsaKey = key.toRSAKey()
        val digest = MessageDigest.getInstance("SHA-256")
        digest.update(rsaKey.modulus.decode())
        digest.update(0)
        digest.update(rsaKey.publicExponent.decode())
        val keyId = Base64.getUrlEncoder().withoutPadding().encodeToString(digest.digest())
        return RSAKey.Builder(rsaKey).keyID(keyId).build()
    }

    @Synchronized
    fun verifyToken(token: String) {
        getOrCreateJwtProcessor().process(JWTParser.parse(token), null)
    }

    class TokenBuilder(private val builder: JWTClaimsSet.Builder) {
        val claimSetBuilder: JWTClaimsSet.Builder get() = builder
        fun claim(name: String, value: String) {
            builder.claim(name, value)
        }
        fun claim(name: String, value: Long) {
            builder.claim(name, value)
        }
    }

    private inner class PemFileJWKSet<C : SecurityContext>(pemFile: File) : FileJWKSet<C>(pemFile) {
        override fun readFile(): JWKSet {
            return JWKSet(ensureValidKey(JWK.parseFromPEMEncodedObjects(file.readText())))
        }

        override fun toString(): String {
            return "PemFileJWKSet[$file]"
        }
    }

    private open inner class FileJWKSet<C : SecurityContext>(val file: File) : JWKSource<C> {
        private var loadedAt: Long = 0
        private var cached: JWKSet? = null

        open fun readFile(): JWKSet {
            return JWKSet(JWK.parse(file.readText()))
        }

        override fun get(jwkSelector: JWKSelector, context: C?): List<JWK?>? {
            val jwks = cached.takeIf { System.currentTimeMillis() - loadedAt < fileRefreshTime.inWholeMilliseconds }
                ?: readFile().let { jwks ->
                    JWKSet(
                        jwks.keys.flatMap { key ->
                            if (key.isPrivate) {
                                listOf(key, key.toPublicJWK())
                            } else {
                                listOf(key)
                            }
                        },
                    )
                }.also {
                    cached = it
                    loadedAt = System.currentTimeMillis()
                }
            return jwkSelector.select(jwks)
        }

        override fun toString(): String {
            return "FileJWKSet[$file]"
        }
    }

    companion object {
        fun extractUserId(jwt: Payload): String? {
            return jwt.getClaim(KeycloakTokenConstants.EMAIL)?.asString()
                ?: jwt.getClaim(KeycloakTokenConstants.PREFERRED_USERNAME)?.asString()
        }
    }
}

class KtorResourceRetriever(val client: HttpClient) : AbstractRestrictedResourceRetriever(1000, 1000, 0) {
    override fun retrieveResource(url: URL): Resource? {
        return runBlocking {
            val response = client.get(url.toString())
            Resource(response.bodyAsText(), response.contentType()?.toString())
        }
    }
}

fun JWTPrincipal.getUserName(): String? = ModelixJWTUtil.extractUserId(payload)
