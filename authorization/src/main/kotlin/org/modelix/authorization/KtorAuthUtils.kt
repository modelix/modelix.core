package org.modelix.authorization

import com.auth0.jwt.JWT
import com.auth0.jwt.JWTCreator
import com.auth0.jwt.algorithms.Algorithm
import com.auth0.jwt.interfaces.DecodedJWT
import io.ktor.http.auth.AuthScheme
import io.ktor.http.auth.HttpAuthHeader
import io.ktor.server.application.Application
import io.ktor.server.application.ApplicationCall
import io.ktor.server.application.ApplicationCallPipeline
import io.ktor.server.application.call
import io.ktor.server.application.install
import io.ktor.server.application.plugin
import io.ktor.server.auth.authenticate
import io.ktor.server.auth.parseAuthorizationHeader
import io.ktor.server.auth.principal
import io.ktor.server.request.header
import io.ktor.server.routing.Route
import io.ktor.util.pipeline.PipelineContext
import org.modelix.authorization.permissions.PermissionEvaluator
import org.modelix.authorization.permissions.PermissionParts
import java.time.Instant
import java.time.temporal.ChronoUnit

internal const val MODELIX_JWT_AUTH = "modelixJwtAuth"

@Deprecated("Install the ModelixAuthorization plugin", replaceWith = ReplaceWith("install(ModelixAuthorization) { if (unitTestMode) configureForUnitTests() }"))
fun Application.installAuthentication(unitTestMode: Boolean = false) {
    install(ModelixAuthorization) {
        if (unitTestMode) configureForUnitTests()
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
    requiresLogin {
        intercept(ApplicationCallPipeline.Call) {
            call.checkPermission(resource, scope)
        }
        body()
    }
}

fun Route.requiresLogin(body: Route.() -> Unit) {
    authenticate(MODELIX_JWT_AUTH) {
        body()
    }
}

fun ApplicationCall.checkPermission(resource: KeycloakResource, scope: KeycloakScope) {
    if (!application.getModelixAuthorizationConfig().permissionCheckingEnabled()) return
    val principal = principal<AccessTokenPrincipal>() ?: throw NotLoggedInException()
    if (!KeycloakUtils.hasPermission(principal.jwt, resource, scope)) {
        throw NoPermissionException(principal, resource.name, scope.name)
    }
}

fun PipelineContext<*, ApplicationCall>.checkPermission(permissionParts: PermissionParts) {
    call.checkPermission(permissionParts)
}

fun ApplicationCall.checkPermission(permissionToCheck: PermissionParts) {
    if (!hasPermission(permissionToCheck)) {
        val principal = principal<AccessTokenPrincipal>()
        throw NoPermissionException(principal, null, null, "${principal?.getUserName()} has no permission '$permissionToCheck'")
    }
}

fun ApplicationCall.hasPermission(permissionToCheck: PermissionParts): Boolean {
    return application.plugin(ModelixAuthorization).hasPermission(this, permissionToCheck)
}

fun ApplicationCall.getPermissionEvaluator(): PermissionEvaluator {
    return application.plugin(ModelixAuthorization).getPermissionEvaluator(this)
}

fun createModelixAccessToken(hmac512key: String, user: String, grantedPermissions: List<String>, additionalTokenContent: (JWTCreator.Builder) -> Unit = {}): String {
    return createModelixAccessToken(Algorithm.HMAC512(hmac512key), user, grantedPermissions, additionalTokenContent)
}

/**
 * Creates a valid JWT token that is compatible to servers with the [ModelixAuthorization] plugin installed.
 */
fun createModelixAccessToken(algorithm: Algorithm, user: String, grantedPermissions: List<String>, additionalTokenContent: (JWTCreator.Builder) -> Unit = {}): String {
    return JWT.create()
        .withClaim("preferred_username", user)
        .withClaim("permissions", grantedPermissions)
        .withExpiresAt(Instant.now().plus(12, ChronoUnit.HOURS))
        .also(additionalTokenContent)
        .sign(algorithm)
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

@Deprecated("Use ModelixAuthorizationConfig.nullIfInvalid")
fun DecodedJWT.nullIfInvalid(): DecodedJWT? {
    return ModelixAuthorizationConfig().nullIfInvalid(this)
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
