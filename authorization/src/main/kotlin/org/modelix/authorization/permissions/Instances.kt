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
    val definitions: MutableMap<DefinitionInstanceReference, DefinitionInstance> = HashMap()

    init {
        // definitions without parameters can be instantiated directly without waiting for additional information
        schema.definitions.filter { it.value.parameters.isEmpty() }.forEach {
            instantiateDefinition(DefinitionInstanceReference(it.key, emptyList(), null))
        }
    }

    fun instantiateDefinition(ref: DefinitionInstanceReference): DefinitionInstance {
        definitions[ref]?.let { return it }

        val parentInstance = ref.parent?.let { instantiateDefinition(it) }
        val definitionSchema = requireNotNull((parentInstance?.definitionSchema?.definitions ?: schema.definitions)[ref.name]) {
            "Definition not found: ${ref.name}"
        }
        val newInstance = DefinitionInstance(
            parentInstance,
            definitionSchema,
            ref,
        )
        definitions[ref] = newInstance
        parentInstance?.let { it.childDefinitions[ref] = newInstance }
        definitionSchema.permissions.forEach { newInstance.getOrCreatePermissionInstance(it.key) }

        // definitions without parameters can be instantiated directly without waiting for additional information
        definitionSchema.definitions.filter { it.value.parameters.isEmpty() }.forEach {
            instantiateDefinition(DefinitionInstanceReference(it.key, emptyList(), ref))
        }

        instantiateRelationTargets(newInstance)

        updateIncludes()
        return newInstance
    }

    private fun instantiateRelationTargets(source: DefinitionInstance): Map<String, DefinitionInstance> {
        return schema.relations.filter { it.fromDefinition == source.definitionSchema.name }.associate { relation ->
            relation.fromRole to instantiateTarget(relation, source)
        }
    }

    private fun instantiateTarget(relation: Relation, source: DefinitionInstance): DefinitionInstance {
        val targetSchema = schema.findDefinition(relation.toDefinition)
        val targetRef = DefinitionInstanceReference(
            relation.toDefinition,
            targetSchema.parameters.map { paramName ->
                val expr = checkNotNull(relation.targetParameterValues[paramName]) { "Value for parameter ${targetSchema.name}.$paramName missing in relation ${relation.fromDefinition}.${relation.toRole}" }
                evaluateExpression(expr, source)
            },
            null
        )
        val target = instantiateDefinition(targetRef)
        source.relationTargetInstances[relation.fromRole] = target
        return target
    }

    private fun evaluateExpression(expr: IExpression, source: DefinitionInstance): String {
        return when (expr) {
            is AddPrefix -> expr.prefix + evaluateExpression(expr.expr, source)
            is SourceParameterValue -> source.reference.parameterValues[source.definitionSchema.parameters.indexOf(expr.name)]
        }
    }

    fun getOrCreatePermissionInstance(ref: PermissionInstanceReference): DefinitionInstance.PermissionInstance {
        return instantiateDefinition(ref.definition)
            .getOrCreatePermissionInstance(ref.permissionName)
    }

    fun updateIncludes() {
        definitions.values.forEach { it.updateIncludes() }
    }

    inner class DefinitionInstance(
        val parent: DefinitionInstance?,
        val definitionSchema: Definition,
        val reference: DefinitionInstanceReference,
    ) {
        val childDefinitions: MutableMap<DefinitionInstanceReference, DefinitionInstance> = HashMap()
        val relationTargetInstances: MutableMap<String, DefinitionInstance> = HashMap()
        val permissions: MutableMap<String, PermissionInstance> = HashMap()

        fun getOrCreatePermissionInstance(name: String): PermissionInstance {
            return permissions.getOrPut(name) {
                val permissionSchema = requireNotNull(definitionSchema.permissions[name]) {
                    "Permission '$name' not found in $reference"
                }
                PermissionInstance(permissionSchema, PermissionInstanceReference(name, reference))
            }
        }

        fun updateIncludes() {
            childDefinitions.values.forEach { it.updateIncludes() }
            permissions.values.forEach { it.updateIncludes() }
        }

        fun resolveDefinitionInstance(name: String): DefinitionInstance? {
            if (definitionSchema.name == name) return this

            childDefinitions.values.firstOrNull { it.definitionSchema.name == name && it.definitionSchema.parameters.isEmpty() }?.let { return it }
            if (parent != null) {
                parent.resolveDefinitionInstance(name)
            } else {
                definitions.values.firstOrNull { it.definitionSchema.name == name && it.definitionSchema.parameters.isEmpty() }
            }?.let { return it }

            relationTargetInstances.values.asSequence().map { it.resolveDefinitionInstance(name) }.firstOrNull()?.let { return it }

            return null
        }

        override fun toString() = reference.toString()

        inner class PermissionInstance(val permissionSchema: Permission, val ref: PermissionInstanceReference) {
            val includedIn: MutableSet<PermissionInstance> = HashSet()
            val includes: MutableSet<PermissionInstance> = HashSet()

            fun updateIncludes() {
                permissionSchema.includedIn.forEach { target ->
                    resolveDefinitionInstance(target.definitionName)?.let {
                        val targetPermission = it.getOrCreatePermissionInstance(target.permissionName)
                        includedIn += targetPermission
                        targetPermission.includes += this
                    }
                }
                permissionSchema.includes.forEach { target ->
                    resolveDefinitionInstance(target.definitionName)?.let {
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

data class DefinitionInstanceReference(
    val name: String,
    val parameterValues: List<String>,
    val parent: DefinitionInstanceReference?,
) {
    override fun toString(): String {
        return (listOfNotNull(parent?.toString()) + name + parameterValues).joinToString("/")
    }
}

data class PermissionInstanceReference(val permissionName: String, val definition: DefinitionInstanceReference) {
    override fun toString(): String {
        return "$definition/$permissionName"
    }
}
