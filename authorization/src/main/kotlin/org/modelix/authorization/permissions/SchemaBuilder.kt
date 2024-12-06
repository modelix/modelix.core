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

fun buildPermissionSchema(body: SchemaBuilder.() -> Unit): Schema {
    return SchemaBuilder().apply { extends(PermissionSchemaBase.SCHEMA) }.also(body).build()
}

/**
 * A Kotlin DSL for defining a [Schema].
 */
class SchemaBuilder {
    private val resourceBuilders: MutableMap<String, ResourceBuilder> = mutableMapOf()

    fun extends(schema: Schema) {
        load(schema)
    }

    private fun load(schema: Schema) {
        schema.resources.forEach { (resourceName, resource) ->
            resource(resourceName) {
                load(resource)
            }
        }
    }

    fun build() = Schema(
        resources = resourceBuilders.mapValues { it.value.build() },
    )

    fun resource(name: String, body: ResourceBuilder.() -> Unit = {}) {
        resourceBuilders.getOrPut(name) { ResourceBuilder(name) }.also(body)
    }

    inner class ResourceBuilder(private val resourceName: String) {
        private val permissionBuilders: MutableMap<String, PermissionBuilder> = LinkedHashMap()
        private val parameters: MutableMap<String, ParameterBuilder> = LinkedHashMap()
        private val innerResourceBuilders: MutableMap<String, ResourceBuilder> = LinkedHashMap()

        fun build(): Resource = Resource(
            resourceName,
            parameters.map { it.value.parameterName },
            innerResourceBuilders.mapValues { it.value.build() },
            permissionBuilders.mapValues { it.value.build() },
        )

        fun load(resource: Resource) {
            resource.parameters.forEach { parameterName ->
                parameter(parameterName)
            }
            resource.permissions.forEach { (permissionName, permission) ->
                permission(permissionName) {
                    load(permission)
                }
            }
            resource.resources.forEach { (childName, child) ->
                resource(childName) {
                    load(child)
                }
            }
        }

        fun resource(name: String, body: ResourceBuilder.() -> Unit = {}) {
            innerResourceBuilders[name] = ResourceBuilder(name).also(body)
        }

        fun permission(name: String, body: PermissionBuilder.() -> Unit = {}): PermissionBuilder {
            return permissionBuilders.getOrPut(name) { PermissionBuilder(name) }.also(body)
        }

        fun parameter(name: String): ParameterBuilder {
            return ParameterBuilder(name).also { parameters[name] = it }
        }

        fun existingParameter(name: String): ParameterBuilder {
            return checkNotNull(parameters[name]) { "Parameter not found: $name" }
        }

        inner class ParameterBuilder(val parameterName: String)

        inner class PermissionBuilder(private val permissionName: String) {
            private var description: String? = null
            private val includedIn: MutableList<ScopedPermissionName> = ArrayList()
            private val includes: MutableList<ScopedPermissionName> = ArrayList()

            fun build(): Permission {
                return Permission(permissionName, description, includedIn, includes)
            }

            fun load(permission: Permission) {
                permission.description?.let { description(it) }
                permission.includedIn.forEach { target ->
                    includedIn(target.resourceName, target.permissionName)
                }
                permission.includes.forEach { target ->
                    includes(target.resourceName, target.permissionName)
                }
            }

            fun permission(name: String, body: PermissionBuilder.() -> Unit = {}) {
                this@ResourceBuilder.permission(name, body).also { it.includedIn(resourceName, permissionName) }
            }

            fun description(newDescription: String) {
                description = newDescription
            }

            fun includedIn(permissionName: String) = includedIn(resourceName, permissionName)

            fun includedIn(resourceName: String, permissionName: String) {
                includedIn += ScopedPermissionName(resourceName, permissionName)
            }

            fun includes(permissionName: String) = includes(resourceName, permissionName)

            fun includes(resourceName: String, permissionName: String) {
                includes += ScopedPermissionName(resourceName, permissionName)
            }
        }
    }
}
