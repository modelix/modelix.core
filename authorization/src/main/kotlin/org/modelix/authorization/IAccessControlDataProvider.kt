package org.modelix.authorization

import org.modelix.authorization.permissions.PermissionParts

interface IAccessControlDataProvider {
    fun getGrantedPermissionsForUser(userId: String): Set<PermissionParts>
    fun getGrantedPermissionsForRole(role: String): Set<PermissionParts>
}

class EmptyAccessControlDataProvider : IAccessControlDataProvider {
    override fun getGrantedPermissionsForUser(userId: String): Set<PermissionParts> {
        return emptySet()
    }

    override fun getGrantedPermissionsForRole(role: String): Set<PermissionParts> {
        return emptySet()
    }
}
