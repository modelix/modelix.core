package org.modelix.authorization

import com.auth0.jwt.interfaces.Payload

class AccessTokenPrincipal(val jwt: Payload) {
    fun getUserName(): String? = ModelixJWTUtil.extractUserId(jwt)

    override fun equals(other: Any?): Boolean {
        if (other !is AccessTokenPrincipal) return false
        return other.jwt.claims == jwt.claims
    }

    override fun hashCode(): Int {
        return jwt.claims.hashCode()
    }
}
