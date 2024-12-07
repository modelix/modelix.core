package org.modelix.authorization

import com.auth0.jwt.interfaces.DecodedJWT
import io.ktor.server.auth.Principal

class AccessTokenPrincipal(val jwt: DecodedJWT) : Principal {
    fun getUserName(): String? = jwt.getClaim("email")?.asString()
        ?: jwt.getClaim("preferred_username")?.asString()

    override fun equals(other: Any?): Boolean {
        if (other !is AccessTokenPrincipal) return false
        return other.jwt.token.equals(jwt.token)
    }

    override fun hashCode(): Int {
        return jwt.token.hashCode()
    }
}
