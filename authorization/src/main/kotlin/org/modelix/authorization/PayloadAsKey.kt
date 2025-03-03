package org.modelix.authorization

import com.auth0.jwt.interfaces.Payload

/**
 * Payload implementations usually don't implement equals/hashCode
 */
class PayloadAsKey(val payload: Payload) {

    override fun equals(other: Any?): Boolean {
        if (other !is PayloadAsKey) return false
        return other.payload.claims == payload.claims
    }

    override fun hashCode(): Int {
        return payload.claims.hashCode()
    }
}
