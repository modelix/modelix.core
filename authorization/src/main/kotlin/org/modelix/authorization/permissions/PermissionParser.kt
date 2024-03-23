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

import org.modelix.authorization.UnknownPermissionException

class PermissionParser(val schema: Schema) {
    fun parse(id: String): PermissionInstanceReference {
        return parse(PermissionParts.fromString(id))
    }

    fun parse(parts: PermissionParts): PermissionInstanceReference {
        val resource = schema.resources[parts.current()]
            ?: throw UnknownPermissionException(parts.fullId, parts.current())
        return parse(parts.next(), resource, null)
    }

    private fun parse(parts: PermissionParts, resource: Resource, parent: ResourceInstanceReference?): PermissionInstanceReference {
        val parameterValues = parts.take(resource.parameters.size)
        val instance = ResourceInstanceReference(resource.name, parameterValues, parent)
        return parse(parts.next(parameterValues.size), resource to instance)
    }

    private fun parse(parts: PermissionParts, resourceInstance: Pair<Resource, ResourceInstanceReference>): PermissionInstanceReference {
        if (parts.remainingSize() == 0) throw UnknownPermissionException(parts.fullId, null)
        if (parts.remainingSize() == 1) {
            val permission = resourceInstance.first.permissions[parts.current()]
            if (permission != null) {
                return PermissionInstanceReference(permission.name, resourceInstance.second)
            }
        }

        val childResource = resourceInstance.first.resources[parts.current()]
        if (childResource != null) {
            return parse(parts.next(), childResource, resourceInstance.second)
        }

        throw UnknownPermissionException(parts.fullId, parts.current())
    }
}
