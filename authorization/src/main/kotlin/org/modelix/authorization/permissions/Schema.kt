package org.modelix.authorization.permissions

import kotlinx.serialization.Serializable

/**
 * An abstract description of the available permissions and the relation between them.
 */
@Serializable
data class Schema(
    val resources: Map<String, Resource>,
) {
    fun findResource(name: String): Resource {
        return requireNotNull(resources.values.firstNotNullOfOrNull { it.findResource(name) }) { "Resource not found: $name" }
    }
}

@Serializable
data class Resource(
    val name: String,
    val parameters: List<String>,
    val resources: Map<String, Resource>,
    val permissions: Map<String, Permission>,
) {
    fun findResource(name: String): Resource? {
        if (name == this.name) return this
        return resources.values.asSequence().mapNotNull { it.findResource(name) }.firstOrNull()
    }
}

@Serializable
data class Permission(
    val name: String,
    val description: String? = null,
    val includedIn: List<ScopedPermissionName>,
    val includes: List<ScopedPermissionName>,
)

@Serializable
data class ScopedPermissionName(val resourceName: String, val permissionName: String) {
    override fun toString(): String {
        return "$resourceName/$permissionName"
    }
}

@Serializable
sealed interface IExpression

@Serializable
data class SourceParameterValue(val name: String) : IExpression

@Serializable
data class AddPrefix(val prefix: String, val expr: IExpression) : IExpression
