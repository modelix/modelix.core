package org.modelix.authorization

class UnknownPermissionException(val permissionId: String, val unknownElement: String?, cause: Exception? = null) : Exception("Unknown permission: $permissionId ($unknownElement)", cause)
