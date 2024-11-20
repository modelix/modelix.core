package org.modelix.authorization

import com.auth0.jwt.JWT
import com.auth0.jwt.interfaces.DecodedJWT
import com.nimbusds.jwt.JWTClaimsSet
import io.ktor.http.auth.AuthScheme
import io.ktor.http.auth.HttpAuthHeader
import io.ktor.server.application.Application
import io.ktor.server.application.ApplicationCall
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

internal const val MODELIX_JWT_AUTH = "modelixJwtAuth"

@Deprecated("Install the ModelixAuthorization plugin", replaceWith = ReplaceWith("install(ModelixAuthorization) { if (unitTestMode) configureForUnitTests() }"))
fun Application.installAuthentication(unitTestMode: Boolean = false) {
    install(ModelixAuthorization) {
        if (unitTestMode) configureForUnitTests()
    }
}

fun Route.requiresLogin(body: Route.() -> Unit) {
    authenticate(MODELIX_JWT_AUTH) {
        body()
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

fun createModelixAccessToken(hmac512key: String, user: String, grantedPermissions: List<String>, additionalTokenContent: (JWTClaimsSet.Builder) -> Unit = {}): String {
    return ModelixJWTUtil().also {
        it.setHmac512Key(hmac512key)
    }.createAccessToken(user, grantedPermissions) {
        additionalTokenContent(it.claimSetBuilder)
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

@Deprecated("Use ModelixAuthorizationConfig.nullIfInvalid")
fun DecodedJWT.nullIfInvalid(): DecodedJWT? {
    return ModelixAuthorizationConfig().nullIfInvalid(this)
}
