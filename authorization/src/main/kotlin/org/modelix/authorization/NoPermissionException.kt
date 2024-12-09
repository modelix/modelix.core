package org.modelix.authorization

class NoPermissionException(val user: AccessTokenPrincipal?, val resourceId: String?, val scope: String?, message: String) :
    RuntimeException(message) {

    constructor(message: String) :
        this(null, null, null, message)
    constructor(user: AccessTokenPrincipal, permissionId: String, type: String) :
        this(user, permissionId, type, "${user.getUserName()} has no $type permission on '$permissionId'")
}
