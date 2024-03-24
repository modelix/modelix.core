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

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import org.modelix.authorization.UnknownPermissionException


class PermissionParts(val fullId: String, val parts: List<String>, val currentIndex: Int) {
    constructor(id: String) : this(id, id.split('/'), 0)
    fun next() = next(1)
    fun next(n: Int) = PermissionParts(fullId, parts, currentIndex + n)
    fun current() = parts[currentIndex]
    fun take(n: Int) = parts.drop(currentIndex).take(n)
    fun remainingSize() = parts.size - currentIndex
}


class PermissionParser(val schema: Schema) {
    fun parse(id: String): PermissionInstanceReference {
        return parse(PermissionParts(id))
    }

    private fun parse(parts: PermissionParts): PermissionInstanceReference {
        val definition = schema.definitions[parts.current()]
            ?: throw UnknownPermissionException(parts.fullId, parts.current())
        return parse(parts.next(), definition, null)
    }

    private fun parse(parts: PermissionParts, definition: Definition, parent: Pair<Definition, DefinitionInstanceReference>?): PermissionInstanceReference {
        val parameterValues = parts.take(definition.parameters.size)
        val instance = DefinitionInstanceReference(definition.name, parameterValues, parent?.second)
        return parse(parts.next(parameterValues.size), definition to instance)
    }

    private fun parse(parts: PermissionParts, definitionInstance: Pair<Definition, DefinitionInstanceReference>): PermissionInstanceReference {
        if (parts.remainingSize() == 0) throw UnknownPermissionException(parts.fullId, null)
        if (parts.remainingSize() == 1) {
            val permission = definitionInstance.first.permissions[parts.current()]
            if (permission != null) {
                return PermissionInstanceReference(permission.name, definitionInstance.second)
            }
        }

        val childDefinition = definitionInstance.first.definitions[parts.current()]
        if (childDefinition != null) {
            return parse(parts.next(), childDefinition, definitionInstance)
        }


        throw UnknownPermissionException(parts.fullId, parts.current())
    }
}

class PermissionEvaluator(val schemaInstance: SchemaInstance) {
    private val allGrantedPermissions: MutableSet<PermissionInstanceReference> = HashSet()
    private val parser = PermissionParser(schemaInstance.schema)

    fun grantPermission(permissionId: String) {
        grantPermission(parser.parse(permissionId))
    }

    fun grantPermission(permissionRef: PermissionInstanceReference) {
        allGrantedPermissions += permissionRef
    }

    fun hasPermission(permissionId: String): Boolean {
        return hasPermission(parser.parse(permissionId))
    }

    fun hasPermission(permissionInstanceRef: PermissionInstanceReference): Boolean {
        if (allGrantedPermissions.contains(permissionInstanceRef)) return true

        val permissionInstance = schemaInstance.getOrCreatePermissionInstance(permissionInstanceRef)
        return permissionInstance.includedIn.any { hasPermission(it.ref) }
    }
}
