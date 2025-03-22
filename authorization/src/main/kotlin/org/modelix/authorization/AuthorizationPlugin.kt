package org.modelix.authorization

import com.auth0.jwt.JWT
import com.auth0.jwt.algorithms.Algorithm
import com.auth0.jwt.exceptions.JWTVerificationException
import com.auth0.jwt.interfaces.DecodedJWT
import com.auth0.jwt.interfaces.JWTVerifier
import com.google.common.cache.CacheBuilder
import com.nimbusds.jose.jwk.JWKSet
import com.nimbusds.jwt.proc.BadJWTException
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.auth.HttpAuthHeader
import io.ktor.server.application.Application
import io.ktor.server.application.ApplicationCall
import io.ktor.server.application.ApplicationCallPipeline
import io.ktor.server.application.BaseRouteScopedPlugin
import io.ktor.server.application.install
import io.ktor.server.application.plugin
import io.ktor.server.auth.Authentication
import io.ktor.server.auth.AuthenticationContext
import io.ktor.server.auth.AuthenticationProvider
import io.ktor.server.auth.UnauthorizedResponse
import io.ktor.server.auth.authenticate
import io.ktor.server.auth.jwt.JWTPrincipal
import io.ktor.server.auth.jwt.jwt
import io.ktor.server.auth.parseAuthorizationHeader
import io.ktor.server.auth.principal
import io.ktor.server.plugins.forwardedheaders.XForwardedHeaders
import io.ktor.server.plugins.statuspages.StatusPages
import io.ktor.server.request.uri
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
import org.modelix.kotlin.utils.filterNotNullValues
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
                            .sign(Algorithm.none())
                        context.principal(JWTPrincipal(JWT.decode(token)))
                    }
                })
            } else {
                // "Authorization: Bearer ..." header is provided in the header by OAuth proxy
                jwt(MODELIX_JWT_AUTH) {
                    realm = "modelix"
                    authHeader { call ->
                        call.request.headers["X-Forwarded-Access-Token"]
                            ?.let { HttpAuthHeader.Single("Bearer", it) }
                            ?: call.request.parseAuthorizationHeader()
                    }

                    verifier(config.getVerifier())
                    challenge { scheme, realm ->
                        call.respond(
                            UnauthorizedResponse(
                                HttpAuthHeader.Parameterized(
                                    scheme,
                                    mapOf(
                                        HttpAuthHeader.Parameters.Realm to realm,
                                        "error" to "invalid_token",
                                        "authorization_uri" to System.getenv("MODELIX_AUTHORIZATION_URI")?.takeIf { it.isNotBlank() },
                                        "token_uri" to System.getenv("MODELIX_TOKEN_URI")?.takeIf { it.isNotBlank() },
                                    ).filterNotNullValues(),
                                ),
                            ),
                        )

                        // login and token generation is done by OAuth proxy. Only validation is required here.
                    }
                    validate { credential ->
                        val jwt = credential.payload
                        application.launch(Dispatchers.IO) {
                            val authPlugin = application.plugin(ModelixAuthorization)
                            val authConfig = authPlugin.config
                            val accessControlPersistence = authConfig.accessControlPersistence
                            accessControlPersistence.recordKnownUser(authConfig.jwtUtil.extractUserId(jwt))
                            accessControlPersistence.recordKnownRoles(authConfig.jwtUtil.extractUserRoles(jwt))
                        }
                        JWTPrincipal(jwt)
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
                    LOG.error(cause) { call.request.uri }
                    call.respondText(text = "500: $cause", status = HttpStatusCode.InternalServerError)
                }
            }
        }

        application.routing {
            authenticate(MODELIX_JWT_AUTH) {
                (installedIntoRoute ?: this).apply {
                    if (config.debugEndpointsEnabled) {
                        get("/user") {
                            val jwt = call.getUnverifiedJwt()
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
                                |User ID: ${config.jwtUtil.extractUserId(jwt)}
                                |Roles: ${config.jwtUtil.extractUserRoles(jwt).joinToString(", ")}
                                |Permissions: ${config.jwtUtil.extractPermissions(jwt)?.joinToString(", ")}
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

    private val permissionCache = CacheBuilder.newBuilder()
        .expireAfterWrite(5, TimeUnit.SECONDS)
        .build<Pair<PayloadAsKey, PermissionInstanceReference>, Boolean>()

    fun hasPermission(call: ApplicationCall, permissionToCheck: PermissionParts): Boolean {
        return hasPermission(call, PermissionParser(config.permissionSchema).parse(permissionToCheck))
    }

    fun hasPermission(call: ApplicationCall, permissionToCheck: PermissionInstanceReference): Boolean {
        if (!config.permissionCheckingEnabled()) return true

        val principal = call.principal<JWTPrincipal>() ?: throw NotLoggedInException()
        return permissionCache.get(PayloadAsKey(principal.payload) to permissionToCheck) {
            getPermissionEvaluator(principal).hasPermission(permissionToCheck)
        }
    }

    fun getPermissionEvaluator(call: ApplicationCall): PermissionEvaluator {
        return getPermissionEvaluator(call.principal())
    }

    fun getPermissionEvaluator(principal: JWTPrincipal?): PermissionEvaluator {
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

    fun loadGrantedPermissions(principal: JWTPrincipal, evaluator: PermissionEvaluator) {
        config.jwtUtil.loadGrantedPermissions(principal.payload, evaluator)
    }
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
        try {
            if (jwt == null) {
                throw JWTVerificationException("No JWT provided.")
            }
            return this@getVerifier.nullIfInvalid(jwt) ?: throw JWTVerificationException("JWT invalid.")
        } catch (ex: BadJWTException) {
            throw JWTVerificationException("Invalid token: ${jwt?.token}", ex)
        }
    }
}
