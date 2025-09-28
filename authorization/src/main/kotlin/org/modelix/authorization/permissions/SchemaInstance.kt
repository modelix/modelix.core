package org.modelix.authorization.permissions

import kotlinx.serialization.Serializable
import org.modelix.authorization.UnknownPermissionException

/**
 * Instantiates the abstract schema with data from an actual system.
 */
class SchemaInstance(val schema: Schema) {
    /**
     * Also contains child instances
     */
    val resources: MutableMap<ResourceInstanceReference, ResourceInstance> = LinkedHashMap()

    init {
        // resources without parameters can be instantiated directly without waiting for additional information
        schema.resources.filter { it.value.parameters.isEmpty() }.forEach {
            instantiateResource(ResourceInstanceReference(it.key, emptyList(), null))
        }
    }

    fun getAllPermissions() = resources.values.flatMap { it.permissions.values }

    fun instantiateResource(ref: ResourceInstanceReference): ResourceInstance {
        resources[ref]?.let { return it }

        val parentInstance = ref.parent?.let { instantiateResource(it) }
        val resourceSchema = requireNotNull((parentInstance?.resourceSchema?.resources ?: schema.resources)[ref.name]) {
            "Resource not found: ${ref.name}"
        }
        val newInstance = ResourceInstance(
            parentInstance,
            resourceSchema,
            ref,
        )
        resources[ref] = newInstance
        parentInstance?.let { it.childResources[ref] = newInstance }
        resourceSchema.permissions.forEach { newInstance.getOrCreatePermissionInstance(it.key) }

        // resources without parameters can be instantiated directly without waiting for additional information
        resourceSchema.resources.filter { it.value.parameters.isEmpty() }.forEach {
            instantiateResource(ResourceInstanceReference(it.key, emptyList(), ref))
        }

        updateIncludes()
        return newInstance
    }

    private fun evaluateExpression(expr: IExpression, source: ResourceInstance): String {
        return when (expr) {
            is AddPrefix -> expr.prefix + evaluateExpression(expr.expr, source)
            is SourceParameterValue -> source.reference.parameterValues[source.resourceSchema.parameters.indexOf(expr.name)]
        }
    }

    fun instantiatePermission(ref: PermissionInstanceReference): ResourceInstance.PermissionInstance {
        return instantiateResource(ref.resource)
            .getOrCreatePermissionInstance(ref.permissionName)
    }

    fun updateIncludes() {
        resources.values.forEach { it.updateIncludes() }
    }

    inner class ResourceInstance(
        val parent: ResourceInstance?,
        val resourceSchema: Resource,
        val reference: ResourceInstanceReference,
    ) {
        val childResources: MutableMap<ResourceInstanceReference, ResourceInstance> = LinkedHashMap()
        val permissions: MutableMap<String, PermissionInstance> = LinkedHashMap()

        fun getOrCreatePermissionInstance(name: String): PermissionInstance {
            return permissions.getOrPut(name) {
                val permissionSchema = resourceSchema.permissions[name]
                if (permissionSchema == null) throw UnknownPermissionException("", unknownElement = name)
                PermissionInstance(permissionSchema, PermissionInstanceReference(name, reference))
            }
        }

        fun updateIncludes() {
            childResources.values.forEach { it.updateIncludes() }
            permissions.values.forEach { it.updateIncludes() }
        }

        fun resolveResourceInstance(name: String): ResourceInstance? {
            if (resourceSchema.name == name) return this

            childResources.values.firstOrNull { it.resourceSchema.name == name && it.resourceSchema.parameters.isEmpty() }?.let { return it }
            if (parent != null) {
                parent.resolveResourceInstance(name)
            } else {
                resources.values.firstOrNull { it.resourceSchema.name == name && it.resourceSchema.parameters.isEmpty() }
            }?.let { return it }

            return null
        }

        override fun toString() = reference.toString()

        inner class PermissionInstance(val permissionSchema: Permission, val ref: PermissionInstanceReference) {
            val includedIn: MutableSet<PermissionInstance> = HashSet()
            val includes: MutableSet<PermissionInstance> = HashSet()

            fun transitiveIncludes(acc: MutableSet<PermissionInstance> = LinkedHashSet()): Set<PermissionInstance> {
                for (p in includes) {
                    acc.add(p)
                    p.transitiveIncludes(acc)
                }
                return acc
            }

            fun transitiveIncludedIn(acc: MutableSet<PermissionInstance> = LinkedHashSet()): Set<PermissionInstance> {
                for (p in includedIn) {
                    if (acc.contains(p)) continue
                    acc.add(p)
                    p.transitiveIncludedIn(acc)
                }
                return acc
            }

            fun updateIncludes() {
                permissionSchema.includedIn.forEach { target ->
                    resolveResourceInstance(target.resourceName)?.let {
                        val targetPermission = it.getOrCreatePermissionInstance(target.permissionName)
                        includedIn += targetPermission
                        targetPermission.includes += this
                    }
                }
                permissionSchema.includes.forEach { target ->
                    resolveResourceInstance(target.resourceName)?.let {
                        val targetPermission = it.getOrCreatePermissionInstance(target.permissionName)
                        includes += targetPermission
                        targetPermission.includedIn += this
                    }
                }
            }

            override fun toString() = ref.toString()
        }
    }
}

@Serializable
data class ResourceInstanceReference(
    val name: String,
    val parameterValues: List<String>,
    val parent: ResourceInstanceReference?,
) {
    val fullId: String get() = toPermissionParts().fullId

    override fun toString(): String {
        return toPermissionParts().toString()
    }

    fun toPermissionParts(): PermissionParts {
        return (parent?.toPermissionParts() ?: PermissionParts()) + name + parameterValues
    }

    fun getWildcardMutations(): Sequence<ResourceInstanceReference> {
        val mutateSelf = parameterValues.size == 1 && parameterValues.single() != WILDCARD
        val parentMutations = parent?.getWildcardMutations() ?: emptySequence()
        return if (mutateSelf) {
            sequenceOf(parent).map {
                ResourceInstanceReference(name, listOf(WILDCARD), it)
            } + parentMutations.flatMap {
                sequenceOf(
                    ResourceInstanceReference(name, parameterValues, it),
                    ResourceInstanceReference(name, listOf(WILDCARD), it),
                )
            }
        } else {
            parentMutations.map { ResourceInstanceReference(name, parameterValues, it) }
        }
    }

    fun containsWildcards(): Boolean {
        return parameterValues.contains(WILDCARD) || parent != null && parent.containsWildcards()
    }

    companion object {
        const val WILDCARD = "*"
    }
}

data class PermissionInstanceReference(val permissionName: String, val resource: ResourceInstanceReference) {
    fun toPermissionParts() = resource.toPermissionParts() + permissionName
    val fullId: String get() = toPermissionParts().fullId
    override fun toString(): String {
        return toPermissionParts().toString()
    }
    fun isValid(schema: Schema): Boolean {
        return try {
            PermissionParser(schema).parse(toPermissionParts())
            true
        } catch (ex: UnknownPermissionException) {
            false
        }
    }
    fun getWildcardMutations(): Sequence<PermissionInstanceReference> {
        return resource.getWildcardMutations().map { PermissionInstanceReference(permissionName, it) }
    }

    fun containsWildcards() = resource.containsWildcards()
}
