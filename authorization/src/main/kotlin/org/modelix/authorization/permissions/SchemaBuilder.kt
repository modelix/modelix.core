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
    return SchemaBuilder().also(body).build()
}

/**
 * A Kotlin DSL for defining a [Schema].
 */
class SchemaBuilder {
    private val resourceBuilders: MutableMap<String, ResourceBuilder> = mutableMapOf()
    private val relationBuilders: MutableList<RelationBuilder> = mutableListOf()

    fun extends(schema: Schema) {
        load(schema)
    }

    private fun load(schema: Schema) {
        schema.resources.forEach { (resourceName, resource) ->
            resource(resourceName) {
                load(resource)
            }
        }
        schema.relations.forEach { relation ->
            resource(relation.fromResource) {
                relation(relation.fromRole) {
                    targetResource(relation.toResource)
                    relation.toRole?.let { targetRole(it) }
                    relation.targetParameterValues.forEach { param ->
                        targetParameterValue(param.key, param.value)
                    }
                }
            }
        }
    }

    fun build() = Schema(
        resources = resourceBuilders.mapValues { it.value.build() },
        relations = relationBuilders.map { it.build() },
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

        fun relation(name: String, body: RelationBuilder.() -> Unit = {}): RelationBuilder {
            return RelationBuilder(resourceName, name).also { relationBuilders += it }.also(body)
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

    inner class RelationBuilder(val fromResource: String, val fromRole: String) {
        private var toResource: String? = null
        private var toRole: String? = null
        private val targetParameterValues: MutableMap<String, IExpression> = LinkedHashMap()

        fun build(): Relation {
            checkNotNull(fromResource)
            checkNotNull(toResource) { "$fromResource.$fromRole -> $toResource.$toRole" }
            return Relation(
                fromResource,
                fromRole,
                toResource!!,
                toRole,
                targetParameterValues,
            )
        }

        fun to(resource: String, role: String) {
            this.toResource = resource
            this.toRole = role
        }

        fun targetResource(targetName: String) {
            toResource = targetName
        }

        fun targetRole(roleName: String) {
            toRole = roleName
        }

        fun targetParameterValue(parameterName: String, parameterValue: IExpression) {
            targetParameterValues[parameterName] = parameterValue
        }

        fun sourceParameterValue(name: String) = SourceParameterValue(name)

        fun IExpression.withPrefix(prefix: String) = AddPrefix(prefix, this)
    }
}
