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

import kotlinx.serialization.json.JsonObject

fun buildSchema(body: SchemaBuilder.() -> Unit): Schema {
    return SchemaBuilder().also(body).build()
}

interface IDefinitionContext {
    fun SchemaBuilder.DefinitionBuilder.ParameterBuilder.get(): String
}

interface IPermissionContext {
    val permissionId: String
    fun <T> SchemaBuilder.DefinitionBuilder.ParameterBuilder.get(): T
}

/**
 * A Kotlin DSL for defining a [Schema].
 */
class SchemaBuilder {
    private val definitionBuilders: MutableMap<String, DefinitionBuilder> = mutableMapOf()
    private val relationBuilders: MutableList<RelationBuilder> = mutableListOf()

    fun extends(schema: Schema) {
        load(schema)
    }

    private fun load(schema: Schema) {
        schema.definitions.forEach { (definitionName, definition) ->
            definition(definitionName) {
                load(definition)
            }
        }
    }

    fun build() = Schema(
        definitions = definitionBuilders.mapValues { it.value.build() },
        relations = relationBuilders.map { it.build() }
    )

    fun definition(name: String, body: DefinitionBuilder.() -> Unit = {}) {
        definitionBuilders.getOrPut(name) { DefinitionBuilder(name) }.also(body)
    }

    fun relation(body: RelationBuilder.() -> Unit = {}): RelationBuilder {
        return RelationBuilder().also { relationBuilders += it }.also(body)
    }

    inner class DefinitionBuilder(private val definitionName: String) {
        private val permissionBuilders: MutableMap<String, PermissionBuilder> = HashMap()
        private val parameters: MutableMap<String, ParameterBuilder> = HashMap()
        private val innerDefinitionBuilders: MutableMap<String, DefinitionBuilder> = mutableMapOf()

        fun build(): Definition = Definition(
            definitionName,
            parameters.map { it.value.parameterName },
            innerDefinitionBuilders.mapValues { it.value.build() },
            permissionBuilders.mapValues { it.value.build() }
        )

        fun load(definition: Definition) {
            definition.parameters.forEach { parameterName ->
                parameter(parameterName)
            }
            definition.permissions.forEach { (permissionName, permission) ->
                permission(permissionName) {
                    permission.description?.let { description(it) }
                    permission.includedIn.forEach { includedIn ->
                        includedIn(includedIn.definitionName, includedIn.permissionName)
                    }
                }
            }
            definition.definitions.forEach { (childName, child) ->
                definition(childName) {
                    load(child)
                }
            }
        }

        fun definition(name: String, body: DefinitionBuilder.() -> Unit = {}) {
            innerDefinitionBuilders[name] = DefinitionBuilder(name).also(body)
        }

        fun relation(name: String, body: RelationBuilder.() -> Unit = {}): RelationBuilder {
            return RelationBuilder().also { it.from(definitionName, name) }.also { relationBuilders += it }.also(body)
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

            fun build(): Permission {
                return Permission(permissionName, description, includedIn)
            }

            fun permission(name: String, body: PermissionBuilder.() -> Unit = {}) {
                this@DefinitionBuilder.permission(name, body).also { it.includedIn(definitionName, permissionName) }
            }

            fun description(newDescription: String) {
                description = newDescription
            }

            fun includedIn(permissionName: String) = includedIn(definitionName, permissionName)

            fun includedIn(definitionName: String, permissionName: String) {
                includedIn += ScopedPermissionName(definitionName, permissionName)
            }
        }
    }

    inner class RelationBuilder {
        private var fromDefinition: String? = null
        private var fromRole: String? = null
        private var toDefinition: String? = null
        private var toRole: String? = null

        fun build(): Relation {
            checkNotNull(fromDefinition)
            checkNotNull(toDefinition) { "$fromDefinition.$fromRole -> $toDefinition.$toRole" }
            return Relation(
                fromDefinition!!,
                fromRole,
                toDefinition!!,
                toRole
            )
        }

        fun from(definition: String, role: String) {
            this.fromDefinition = definition
            this.fromRole = role
        }

        fun to(definition: String, role: String) {
            this.toDefinition = definition
            this.toRole = role
        }

        fun target(definition: String, body: TargetBuilder.() -> Unit) {
            toDefinition = definition
            TargetBuilder().also(body)
        }

        inner class TargetBuilder {
            fun parameterValue(parameterName: String, parameterValue: IDefinitionContext.() -> String?) {  }
            fun role(role: String) { toRole = role }
        }
    }

}

class DefaultAuthorizationInput(val jwt: JsonObject) {

}
