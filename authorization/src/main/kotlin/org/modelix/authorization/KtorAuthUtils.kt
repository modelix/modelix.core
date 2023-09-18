/*
 * Copyright (c) 2023.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * https://www.apache.org/licenses/LICENSE-2.0
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
import com.auth0.jwt.interfaces.DecodedJWT
import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.http.HttpStatusCode
import io.ktor.http.auth.AuthScheme
import io.ktor.http.auth.HttpAuthHeader
import io.ktor.server.application.Application
import io.ktor.server.application.ApplicationCall
import io.ktor.server.application.ApplicationCallPipeline
import io.ktor.server.application.call
import io.ktor.server.application.install
import io.ktor.server.auth.Authentication
import io.ktor.server.auth.AuthenticationContext
import io.ktor.server.auth.AuthenticationProvider
import io.ktor.server.auth.authenticate
import io.ktor.server.auth.jwt.jwt
import io.ktor.server.auth.parseAuthorizationHeader
import io.ktor.server.auth.principal
import io.ktor.server.plugins.forwardedheaders.XForwardedHeaders
import io.ktor.server.plugins.statuspages.StatusPages
import io.ktor.server.request.header
import io.ktor.server.response.respond
import io.ktor.server.response.respondText
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.routing
import io.ktor.util.AttributeKey
import io.ktor.util.pipeline.PipelineContext
import java.security.interfaces.RSAPublicKey

private const val jwtAuth = "jwtAuth"
private val httpClient = HttpClient(CIO)
private val UNIT_TEST_MODE_KEY = AttributeKey<Boolean>("unit-test-mode")

fun Application.installAuthentication(unitTestMode: Boolean = false) {
    install(XForwardedHeaders)
    install(Authentication) {
        if (unitTestMode) {
            register(object : AuthenticationProvider(object : Config(jwtAuth) {}) {
                override suspend fun onAuthenticate(context: AuthenticationContext) {
                    context.call.attributes.put(UNIT_TEST_MODE_KEY, true)
                    val token = JWT.create()
                        .withClaim("email", "unit-tests@example.com")
                        .sign(Algorithm.HMAC256("unit-tests"))
                    context.principal(AccessTokenPrincipal(JWT.decode(token)))
                }
            })
        } else {
            // "Authorization: Bearer ..." header is provided in the header by OAuth proxy
            jwt(jwtAuth) {
                verifier(KeycloakUtils.jwkProvider) {
                    acceptLeeway(60L)
                }
                challenge { _, _ ->
                    call.respond(status = HttpStatusCode.Unauthorized, "No or invalid JWT token provided")
                } // login and token generation is done by OAuth proxy. Only validation is required here.
                validate {
                    try {
                        // OAuth proxy passes the ID token as the bearer token, but we need the access token
                        val token = jwtFromHeaders()
                        if (token != null) {
                            return@validate token.nullIfInvalid()?.let { AccessTokenPrincipal(it) }
                        }
                    } catch (e: Exception) {
                    }
                    null
                }
            }
        }
    }
    install(StatusPages) {
        exception<Throwable> { call, cause ->
            when (cause) {
                is NoPermissionException -> call.respondText(
                    text = cause.message ?: "",
                    status = HttpStatusCode.Forbidden,
                )
                is NotLoggedInException -> call.respondText(
                    text = cause.message ?: "",
                    status = HttpStatusCode.Unauthorized,
                )
                else -> {
                    val text = """
                        |500: $cause
                        |
                        |${cause.stackTraceToString()}
                    """.trimMargin()
                    call.respondText(text = text, status = HttpStatusCode.InternalServerError)
                }
            }
        }
    }
    routing {
        authenticate(jwtAuth) {
            get("/user") {
                val jwt = call.principal<AccessTokenPrincipal>()?.jwt ?: call.jwtFromHeaders()
                if (jwt == null) {
                    call.respondText("No JWT token available")
                } else {
                    val claims = jwt.claims.map { "${it.key}: ${it.value}" }.joinToString("\n")
                    val validationError = try {
                        verifyTokenSignature(jwt)
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
    }
}

fun Route.requiresPermission(resource: KeycloakResource, permissionType: EPermissionType, body: Route.() -> Unit) {
    requiresPermission(resource, permissionType.toKeycloakScope(), body)
}

fun Route.requiresRead(resource: KeycloakResource, body: Route.() -> Unit) {
    requiresPermission(resource, KeycloakScope.READ, body)
}

fun Route.requiresWrite(resource: KeycloakResource, body: Route.() -> Unit) {
    requiresPermission(resource, KeycloakScope.WRITE, body)
}

fun Route.requiresDelete(resource: KeycloakResource, body: Route.() -> Unit) {
    requiresPermission(resource, KeycloakScope.DELETE, body)
}

fun Route.requiresPermission(resource: KeycloakResource, scope: KeycloakScope, body: Route.() -> Unit) {
    authenticate(jwtAuth) {
        intercept(ApplicationCallPipeline.Call) {
            call.checkPermission(resource, scope)
        }
        body()
    }
}

fun Route.requiresLogin(body: Route.() -> Unit) {
    authenticate(jwtAuth) {
        body()
    }
}

fun ApplicationCall.checkPermission(resource: KeycloakResource, scope: KeycloakScope) {
    if (attributes.getOrNull(UNIT_TEST_MODE_KEY) == true) return
    val principal = principal<AccessTokenPrincipal>() ?: throw NotLoggedInException()
    if (!KeycloakUtils.hasPermission(principal.jwt, resource, scope)) {
        throw NoPermissionException(principal, resource.name, scope.name)
    }
}

private fun Map<String, Any>?.readRolesArray(): List<String> {
    return this?.get("roles") as? List<String> ?: emptyList()
}

fun ApplicationCall.getBearerToken(): String? {
    val authHeader = request.parseAuthorizationHeader()
    if (authHeader == null || authHeader.authScheme != AuthScheme.Bearer) return null
    val tokenString = when (authHeader) {
        is HttpAuthHeader.Single -> authHeader.blob
        else -> return null
    }
    return tokenString
}

fun ApplicationCall.jwtFromHeaders(): DecodedJWT? {
    // OAuth proxy passes the ID token as the bearer token, but we need the access token.
    return (request.header("X-Forwarded-Access-Token") ?: getBearerToken())?.let { JWT.decode(it) }
}

fun ApplicationCall.jwt() = principal<AccessTokenPrincipal>()?.jwt ?: jwtFromHeaders()

fun PipelineContext<Unit, ApplicationCall>.getUserName(): String? {
    return call.getUserName()
}

fun ApplicationCall.getUserName(): String? {
    return principal<AccessTokenPrincipal>()?.getUserName()
}

fun verifyTokenSignature(token: DecodedJWT) {
    val jwk = KeycloakUtils.jwkProvider.get(token.keyId)
    val publicKey = jwk.publicKey as? RSAPublicKey ?: throw RuntimeException("Invalid key type")
    val algorithm = when (jwk.algorithm) {
        "RS256" -> Algorithm.RSA256(publicKey, null)
        "RSA384" -> Algorithm.RSA384(publicKey, null)
        "RS512" -> Algorithm.RSA512(publicKey, null)
        else -> throw Exception("Unsupported Algorithm")
    }
    val verifier = JWT.require(algorithm)
        .acceptLeeway(0L)
        .build()
    verifier.verify(token)
}

fun DecodedJWT.nullIfInvalid(): DecodedJWT? {
    return try {
        verifyTokenSignature(this)
        this
    } catch (e: Exception) {
        null
    }
}

private var cachedServiceAccountToken: DecodedJWT? = null
val serviceAccountTokenProvider: () -> String = {
    var token: DecodedJWT? = cachedServiceAccountToken?.nullIfInvalid()
    if (token == null) {
        token = KeycloakUtils.getServiceAccountToken()
        cachedServiceAccountToken = token
    }
    token.token
}
