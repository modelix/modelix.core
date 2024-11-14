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
import io.ktor.server.auth.Authentication
import io.ktor.server.auth.AuthenticationContext
import io.ktor.server.auth.AuthenticationProvider
import io.ktor.server.auth.authenticate
import io.ktor.server.auth.jwt.jwt
import io.ktor.server.auth.principal
import io.ktor.server.html.respondHtml
import io.ktor.server.plugins.forwardedheaders.XForwardedHeaders
import io.ktor.server.response.respond
import io.ktor.server.response.respondText
import io.ktor.server.routing.Route
import io.ktor.server.routing.application
import io.ktor.server.routing.get
import io.ktor.server.routing.routing
import io.ktor.util.AttributeKey
import org.modelix.authorization.permissions.PermissionEvaluator
import org.modelix.authorization.permissions.PermissionParts
import org.modelix.authorization.permissions.SchemaInstance
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
                            .withClaim("email", "unit-tests@example.com")
                            .sign(Algorithm.HMAC256("unit-tests"))
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
                            jwtFromHeaders()?.let(::AccessTokenPrincipal)
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
                call.respondText(JWKSet(listOfNotNull(config.ownPublicKey)).toPublicJWKSet().toString(), ContentType.Application.Json)
            }
        }

        if (config.debugEndpointsEnabled) {
            application.routing {
                authenticate(MODELIX_JWT_AUTH) {
                    (installedIntoRoute ?: this).apply {
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
                        get("permissions") {
                            call.respondHtml {
                                buildPermissionPage(call.getPermissionEvaluator())
                            }
                        }
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
        .build<Pair<AccessTokenPrincipal, PermissionParts>, Boolean>()

    fun hasPermission(call: ApplicationCall, permissionToCheck: PermissionParts): Boolean {
        if (!config.permissionCheckingEnabled()) return true

        val principal = call.principal<AccessTokenPrincipal>() ?: throw NotLoggedInException()
        return permissionCache.get(principal to permissionToCheck) {
            getPermissionEvaluator(principal).hasPermission(permissionToCheck)
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
        val permissions = principal.jwt.claims["permissions"]?.asList(String::class.java)

        // There is a difference between access tokens and identity tokens.
        // An identity token just contains the user ID and the service has to know the granted permissions.
        // An access token has more limited permissions and is issued for a specific task. It contains the list of
        // granted permissions. Since tokens are signed and created by a trusted authority we don't have to check the
        // list of permissions against our own access control data.
        if (permissions != null) {
            permissions.forEach { evaluator.grantPermission(it) }
        } else {
            val userId = principal.getUserName()
            if (userId != null) {
                // TODO load permissions for the user from some external source
            }
        }
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
        if (jwt == null) {
            throw JWTVerificationException("No JWT provided.")
        }
        return this@getVerifier.nullIfInvalid(jwt) ?: throw JWTVerificationException("JWT invalid.")
    }
}
