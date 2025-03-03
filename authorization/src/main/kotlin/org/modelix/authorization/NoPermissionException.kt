package org.modelix.authorization

import io.ktor.server.auth.jwt.JWTPrincipal

class NoPermissionException(val user: JWTPrincipal?, val resourceId: String?, val scope: String?, message: String) :
    RuntimeException(message) {

    constructor(message: String) :
        this(null, null, null, message)
    constructor(user: JWTPrincipal, permissionId: String, type: String) :
        this(user, permissionId, type, "${user.getUserName()} has no $type permission on '$permissionId'")
}
