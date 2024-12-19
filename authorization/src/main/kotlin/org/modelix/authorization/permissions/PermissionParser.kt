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

    fun parseResource(parts: PermissionParts): ResourceInstanceReference? {
        val rootResource = schema.resources[parts.current()]
            ?: throw UnknownPermissionException(parts.fullId, parts.current())
        return parseResource(parts.next(), rootResource, null)
    }

    private fun parseResource(parts: PermissionParts, resource: Resource, parent: ResourceInstanceReference?): ResourceInstanceReference? {
        val parameterValues = parts.take(resource.parameters.size)
        val instance = ResourceInstanceReference(resource.name, parameterValues, parent)
        return parseResource(parts.next(parameterValues.size), resource to instance)
    }

    private fun parseResource(parts: PermissionParts, parentResource: Pair<Resource, ResourceInstanceReference>): ResourceInstanceReference? {
        if (parts.remainingSize() == 0) return parentResource.second
        val childResource = parentResource.first.resources[parts.current()] ?: return null
        return parseResource(parts.next(), childResource, parentResource.second)
    }
}
