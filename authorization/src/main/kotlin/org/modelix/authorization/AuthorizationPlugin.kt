package org.modelix.authorization

import com.auth0.jwt.JWT
import com.auth0.jwt.algorithms.Algorithm
import com.auth0.jwt.exceptions.JWTVerificationException
import com.auth0.jwt.interfaces.DecodedJWT
import com.auth0.jwt.interfaces.JWTVerifier
import com.google.common.cache.CacheBuilder
import com.nimbusds.jose.jwk.JWKSet
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.Application
import io.ktor.server.application.ApplicationCall
import io.ktor.server.application.ApplicationCallPipeline
import io.ktor.server.application.BaseRouteScopedPlugin
import io.ktor.server.application.call
import io.ktor.server.application.install
import io.ktor.server.application.plugin
import io.ktor.server.auth.Authentication
import io.ktor.server.auth.AuthenticationContext
import io.ktor.server.auth.AuthenticationProvider
import io.ktor.server.auth.authenticate
import io.ktor.server.auth.jwt.jwt
import io.ktor.server.auth.principal
import io.ktor.server.plugins.forwardedheaders.XForwardedHeaders
import io.ktor.server.plugins.statuspages.StatusPages
import io.ktor.server.response.respond
import io.ktor.server.response.respondText
import io.ktor.server.routing.Route
import io.ktor.server.routing.application
import io.ktor.server.routing.get
import io.ktor.server.routing.routing
import io.ktor.util.AttributeKey
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.modelix.authorization.permissions.PermissionEvaluator
import org.modelix.authorization.permissions.PermissionInstanceReference
import org.modelix.authorization.permissions.PermissionParser
import org.modelix.authorization.permissions.PermissionParts
import org.modelix.authorization.permissions.SchemaInstance
import org.modelix.authorization.permissions.recordKnownRoles
import org.modelix.authorization.permissions.recordKnownUser
import java.nio.charset.StandardCharsets
import java.util.Base64
import java.util.Collections
import java.util.concurrent.TimeUnit

private val LOG = mu.KotlinLogging.logger { }

/**
 * JWT based authorization plugin.
 */
object ModelixAuthorization : BaseRouteScopedPlugin<IModelixAuthorizationConfig, ModelixAuthorizationPluginInstance> {

    override fun install(
        pipeline: ApplicationCallPipeline,
        configure: IModelixAuthorizationConfig.() -> Unit,
    ): ModelixAuthorizationPluginInstance {
        val config = ModelixAuthorizationConfig().apply(configure)
        val application = when (pipeline) {
            is Route -> pipeline.application
            is Application -> pipeline
            else -> error("Unsupported pipeline type: ${pipeline::class}")
        }
        val installedIntoRoute = pipeline as? Route

        application.install(XForwardedHeaders)
        application.install(Authentication) {
            if (config.shouldGenerateFakeTokens()) {
                register(object : AuthenticationProvider(object : Config(MODELIX_JWT_AUTH) {}) {
                    override suspend fun onAuthenticate(context: AuthenticationContext) {
                        val token = JWT.create()
                            .withIssuer("modelix")
                            .withAudience("modelix")
                            .withClaim(KeycloakTokenConstants.EMAIL, "unit-tests@example.com")
                            .sign(Algorithm.HMAC256("unit-tests"))
                        // The signing algorithm and key isn't relevant because the token is already considered valid
                        // and the signature is never checked.
                        context.principal(AccessTokenPrincipal(JWT.decode(token)))
                    }
                })
            } else {
                // "Authorization: Bearer ..." header is provided in the header by OAuth proxy
                jwt(MODELIX_JWT_AUTH) {
                    verifier(config.getVerifier())
                    challenge { _, _ ->
                        call.respond(status = HttpStatusCode.Unauthorized, "No or invalid JWT token provided")
                        // login and token generation is done by OAuth proxy. Only validation is required here.
                    }
                    validate {
                        try {
                            val authPlugin = application.plugin(ModelixAuthorization)
                            val authConfig = authPlugin.config
                            jwtFromHeaders()
                                ?.let { authConfig.nullIfInvalid(it) }
                                ?.also { jwt ->
                                    application.launch(Dispatchers.IO) {
                                        val accessControlPersistence = authConfig.accessControlPersistence
                                        accessControlPersistence.recordKnownUser(authConfig.jwtUtil.extractUserId(jwt))
                                        accessControlPersistence.recordKnownRoles(authConfig.jwtUtil.extractUserRoles(jwt))
                                    }
                                }
                                ?.let(::AccessTokenPrincipal)
                        } catch (e: Exception) {
                            LOG.warn(e) { "Failed to read JWT token" }
                            null
                        }
                    }
                }
            }
        }

        application.routing {
            get(".well-known/jwks.json") {
                call.respondText(
                    JWKSet(listOfNotNull(config.ownPublicKey)).toPublicJWKSet().toString(),
                    ContentType.Application.Json,
                )
            }
        }

        if (config.installStatusPages) {
            application.install(StatusPages) {
                exception<NotLoggedInException> { call, cause ->
                    call.respondText(text = "401: ${cause.message}", status = HttpStatusCode.Unauthorized)
                }
                exception<NoPermissionException> { call, cause ->
                    call.respondText(text = "403: ${cause.message}", status = HttpStatusCode.Forbidden)
                }
                exception<Throwable> { call, cause ->
                    call.respondText(text = "500: $cause", status = HttpStatusCode.InternalServerError)
                }
            }
        }

        application.routing {
            authenticate(MODELIX_JWT_AUTH) {
                (installedIntoRoute ?: this).apply {
                    if (config.debugEndpointsEnabled) {
                        get("/user") {
                            val jwt = call.principal<AccessTokenPrincipal>()?.jwt ?: call.jwtFromHeaders()
                            if (jwt == null) {
                                call.respondText("No JWT token available")
                            } else {
                                val claims = jwt.claims.map { "${it.key}: ${it.value}" }.joinToString("\n")
                                val validationError = try {
                                    config.verifyTokenSignature(jwt)
                                    "Valid"
                                } catch (e: Exception) {
                                    e.message
                                }
                                call.respondText(
                                    """
                                |Token: ${jwt.token}
                                |
                                |Validation result: $validationError
                                |
                                |$claims
                                |
                                    """.trimMargin(),
                                )
                            }
                        }
                    }
                    if (config.permissionManagementEnabled) {
                        installPermissionManagementHandlers()
                    }
                }
            }
        }

        val pluginInstance = ModelixAuthorizationPluginInstance(config)
        return pluginInstance
    }

    override val key: AttributeKey<ModelixAuthorizationPluginInstance> = AttributeKey("ModelixAuthorization")
}

class ModelixAuthorizationPluginInstance(val config: ModelixAuthorizationConfig) {

    private val deniedPermissionRequests: MutableSet<DeniedPermissionRequest> = Collections.synchronizedSet(LinkedHashSet())
    private val permissionCache = CacheBuilder.newBuilder()
        .expireAfterWrite(5, TimeUnit.SECONDS)
        .build<Pair<AccessTokenPrincipal, PermissionInstanceReference>, Boolean>()

    fun getDeniedPermissions(): Set<DeniedPermissionRequest> = deniedPermissionRequests.toSet()

    fun hasPermission(call: ApplicationCall, permissionToCheck: PermissionParts): Boolean {
        return hasPermission(call, PermissionParser(config.permissionSchema).parse(permissionToCheck))
    }

    fun hasPermission(call: ApplicationCall, permissionToCheck: PermissionInstanceReference): Boolean {
        if (!config.permissionCheckingEnabled()) return true

        val principal = call.principal<AccessTokenPrincipal>() ?: throw NotLoggedInException()
        return permissionCache.get(principal to permissionToCheck) {
            getPermissionEvaluator(principal).hasPermission(permissionToCheck).also { granted ->
                if (!granted) {
                    val userId = principal.getUserName()
                    if (userId != null) {
                        synchronized(deniedPermissionRequests) {
                            deniedPermissionRequests += DeniedPermissionRequest(
                                permissionRef = permissionToCheck,
                                userId = userId,
                                jwtPayload = principal.jwt.payload,
                            )
                            while (deniedPermissionRequests.size >= 100) {
                                deniedPermissionRequests.iterator().also { it.next() }.remove()
                            }
                        }
                    }
                }
            }
        }
    }

    fun getPermissionEvaluator(call: ApplicationCall): PermissionEvaluator {
        return getPermissionEvaluator(call.principal())
    }

    fun getPermissionEvaluator(principal: AccessTokenPrincipal?): PermissionEvaluator {
        val evaluator = createPermissionEvaluator()
        if (principal != null) {
            loadGrantedPermissions(principal, evaluator)
        }
        return evaluator
    }

    fun createPermissionEvaluator(): PermissionEvaluator {
        return PermissionEvaluator(createSchemaInstance())
    }

    fun createSchemaInstance() = SchemaInstance(config.permissionSchema)

    fun loadGrantedPermissions(principal: AccessTokenPrincipal, evaluator: PermissionEvaluator) {
        config.jwtUtil.loadGrantedPermissions(principal.jwt, evaluator)
    }
}

data class DeniedPermissionRequest(
    val permissionRef: PermissionInstanceReference,
    val userId: String,
    val jwtPayload: String,
) {
    fun jwtPayloadJson() = String(Base64.getUrlDecoder().decode(jwtPayload), StandardCharsets.UTF_8)
}

/**
 * Returns an [JWTVerifier] that wraps our common authorization logic,
 * so that it can be configured in the verification with Ktor's JWT authorization.
 */
internal fun ModelixAuthorizationConfig.getVerifier() = object : JWTVerifier {
    override fun verify(token: String?): DecodedJWT {
        val jwt = JWT.decode(token)
        return verify(jwt)
    }

    override fun verify(jwt: DecodedJWT?): DecodedJWT {
        if (jwt == null) {
            throw JWTVerificationException("No JWT provided.")
        }
        return this@getVerifier.nullIfInvalid(jwt) ?: throw JWTVerificationException("JWT invalid.")
    }
}
