package org.modelix.authorization

enum class EPermissionType(vararg val includedTypes: EPermissionType) {
    READ,
    WRITE(READ),
    ;

    fun includes(type: EPermissionType): Boolean = type == this || includedTypes.any { it.includes(type) }
}
