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

        instantiateRelationTargets(newInstance)

        updateIncludes()
        return newInstance
    }

    private fun instantiateRelationTargets(source: ResourceInstance): Map<String, ResourceInstance> {
        return schema.relations.filter { it.fromResource == source.resourceSchema.name }.associate { relation ->
            relation.fromRole to instantiateTarget(relation, source)
        }
    }

    private fun instantiateTarget(relation: Relation, source: ResourceInstance): ResourceInstance {
        val targetSchema = schema.findResource(relation.toResource)
        val targetRef = ResourceInstanceReference(
            relation.toResource,
            targetSchema.parameters.map { paramName ->
                val expr = checkNotNull(relation.targetParameterValues[paramName]) { "Value for parameter ${targetSchema.name}.$paramName missing in relation ${relation.fromResource}.${relation.toRole}" }
                evaluateExpression(expr, source)
            },
            null,
        )
        val target = instantiateResource(targetRef)
        source.relationTargetInstances[relation.fromRole] = target
        return target
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
        val relationTargetInstances: MutableMap<String, ResourceInstance> = LinkedHashMap()
        val permissions: MutableMap<String, PermissionInstance> = LinkedHashMap()

        fun getOrCreatePermissionInstance(name: String): PermissionInstance {
            return permissions.getOrPut(name) {
                val permissionSchema = requireNotNull(resourceSchema.permissions[name]) {
                    "Permission '$name' not found in $reference"
                }
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

            relationTargetInstances.values.asSequence().map { it.resolveResourceInstance(name) }.firstOrNull()?.let { return it }

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

data class ResourceInstanceReference(
    val name: String,
    val parameterValues: List<String>,
    val parent: ResourceInstanceReference?,
) {
    override fun toString(): String {
        return toPermissionParts().toString()
    }

    fun toPermissionParts(): PermissionParts {
        return (parent?.toPermissionParts() ?: PermissionParts()) + name + parameterValues
    }
}

data class PermissionInstanceReference(val permissionName: String, val resource: ResourceInstanceReference) {
    fun toPermissionParts() = resource.toPermissionParts() + permissionName
    override fun toString(): String {
        return toPermissionParts().toString()
    }
}
