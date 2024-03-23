/*
 * Copyright (c) 2024.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.modelix.authorization.permissions

class PermissionEvaluator(val schemaInstance: SchemaInstance) {
    private val allGrantedPermissions: MutableSet<PermissionInstanceReference> = HashSet()
    private val parser = PermissionParser(schemaInstance.schema)

    fun getAllGrantedPermissions(): Set<PermissionInstanceReference> = schemaInstance.getAllPermissions().map { it.ref }.filter { hasPermission(it) }.toSet()

    fun grantPermission(permissionId: PermissionId) {
        grantPermission(parser.parse(permissionId))
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
        val permissionRef = parser.parse(permissionId)
        val instance = schemaInstance.instantiatePermission(permissionRef)
        hasPermission(permissionRef)
        return instance
    }

    fun hasPermission(permissionInstanceRef: PermissionInstanceReference): Boolean {
        if (allGrantedPermissions.contains(permissionInstanceRef)) return true

        val permissionInstance = schemaInstance.instantiatePermission(permissionInstanceRef)
        val indirectlyGranted = permissionInstance.includedIn.any { hasPermission(it.ref) }
        if (indirectlyGranted) allGrantedPermissions.add(permissionInstanceRef)
        return indirectlyGranted
    }
}
