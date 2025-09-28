package org.modelix.authorization.permissions

import org.modelix.authorization.UnknownPermissionException

class PermissionEvaluator(val schemaInstance: SchemaInstance) {
    private val allGrantedPermissions: MutableSet<PermissionInstanceReference> = HashSet()
    private val parser = PermissionParser(schemaInstance.schema)

    fun getAllGrantedPermissions(): Set<PermissionInstanceReference> = schemaInstance.getAllPermissions().map { it.ref }.filter { hasPermission(it) }.toSet()

    fun grantPermission(permissionId: String) {
        grantPermission(PermissionParts.fromString(permissionId))
    }

    fun grantPermission(permissionId: PermissionParts) {
        try {
            grantPermission(parser.parse(permissionId))
        } catch (ex: UnknownPermissionException) {
            // Tokens may also contain permissions for other services.
        }
    }

    fun grantPermission(permissionRef: PermissionInstanceReference) {
        schemaInstance.instantiatePermission(permissionRef)
        allGrantedPermissions += permissionRef
    }

    fun hasPermission(permissionId: String): Boolean {
        return hasPermission(PermissionParts.fromString(permissionId))
    }

    fun hasPermission(permissionId: PermissionParts): Boolean {
        return hasPermission(parser.parse(permissionId))
    }

    fun instantiatePermission(permissionId: String): SchemaInstance.ResourceInstance.PermissionInstance {
        return instantiatePermission(PermissionParts.fromString(permissionId))
    }

    fun instantiatePermission(permissionId: PermissionParts): SchemaInstance.ResourceInstance.PermissionInstance {
        return instantiatePermission(parser.parse(permissionId))
    }

    fun instantiatePermission(permissionRef: PermissionInstanceReference): SchemaInstance.ResourceInstance.PermissionInstance {
        val instance = schemaInstance.instantiatePermission(permissionRef)
        hasPermission(permissionRef) // permissions are instantiated during the check
        return instance
    }

    fun hasPermission(permissionInstanceRef: PermissionInstanceReference): Boolean {
        require(!permissionInstanceRef.containsWildcards()) {
            // An attacker could try to create a resource with the name '*' to get an unintended wildcard grant.
            "A permission containing wildcards is illegal: $permissionInstanceRef"
        }
        for (withWildcards in sequenceOf(permissionInstanceRef) + permissionInstanceRef.getWildcardMutations()) {
            if (checkPermission(withWildcards)) return true
        }
        return false
    }

    private fun checkPermission(permissionInstanceRef: PermissionInstanceReference): Boolean {
        if (allGrantedPermissions.contains(permissionInstanceRef)) return true

        val permissionInstance = schemaInstance.instantiatePermission(permissionInstanceRef)
        val indirectlyGranted = permissionInstance.includedIn.any { checkPermission(it.ref) }
        if (indirectlyGranted) allGrantedPermissions.add(permissionInstanceRef)
        return indirectlyGranted
    }
}
