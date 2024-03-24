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

    fun getOrCreateDefinitionInstance(ref: DefinitionInstanceReference): DefinitionInstance {
        return definitions.getOrPut(ref) {
            val parentInstance = ref.parent?.let { getOrCreateDefinitionInstance(it) }
            val definitionSchema = requireNotNull(
                (parentInstance?.definitionSchema?.definitions ?: schema.definitions)[ref.name]
            ) { "Definition not found: ${ref.name}" }
            DefinitionInstance(
                parentInstance,
                definitionSchema,
                ref
            ).also { child ->
                parentInstance?.let { it.childDefinitions[ref] = child }
                definitionSchema.permissions.forEach { child.getOrCreatePermissionInstance(it.key) }
            }
        }.also {
            updateIncludes() // TODO only execute on put
        }
    }

    fun getOrCreatePermissionInstance(ref: PermissionInstanceReference): DefinitionInstance.PermissionInstance {
        return getOrCreateDefinitionInstance(ref.definition)
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

            // TODO relations

            return parent?.resolveDefinitionInstance(name)
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
            }

            override fun toString() = ref.toString()
        }
    }
}

data class DefinitionInstanceReference(
    val name: String,
    val parameterValues: List<String>,
    val parent: DefinitionInstanceReference?
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




