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

package org.modelix.authorization

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull


data class Schema<Input>(
    val definitions: Map<String, Definition>,
    val relations: List<Relation>
)
data class Definition(
    val name: String,
    val parameters: List<String>,
    val definitions: Map<String, Definition>,
    val permissions: Map<String, Permission>
)
class Relation()
data class Permission(
    val name: String,
    val includedIn: List<PermissionReference>
)

sealed interface Policy {

}

class PermissionParts(val fullId: String, val parts: List<String>, val currentIndex: Int) {
    constructor(id: String) : this(id, id.split('/'), 0)
    fun next() = next(1)
    fun next(n: Int) = PermissionParts(fullId, parts, currentIndex + n)
    fun current() = parts[currentIndex]
    fun take(n: Int) = parts.drop(currentIndex).take(n)
    fun remainingSize() = parts.size - currentIndex
}

abstract class PermissionEvaluator<Input>(val schema: Schema<Input>, val input: Input) {
    fun hasPermission(permissionReference: String): Boolean {
        val explicitPermissions = getPermissionIdsFromInput().toSet()
        if (explicitPermissions.contains(permissionReference)) return true
        return evaluate(PermissionParts(permissionReference))
    }

    private fun evaluate(parts: PermissionParts): Boolean {
        val definition = schema.definitions[parts.current()]
            ?: throw UnknownPermissionException(parts.fullId, parts.current())
        return evaluate(parts.next(), definition, null)
    }

    private fun evaluate(parts: PermissionParts, definition: Definition, parent: DefinitionInstance?): Boolean {
        val parameterValues = parts.take(definition.parameters.size)
        val instance = DefinitionInstance(parent, definition, parameterValues)
        return evaluate(parts.next(parameterValues.size), instance)
    }

    private fun evaluate(parts: PermissionParts, definitionInstance: DefinitionInstance): Boolean {
        if (parts.remainingSize() == 0) throw UnknownPermissionException(parts.fullId, null)
        if (parts.remainingSize() == 1) {
            val permission = definitionInstance.definition.permissions[parts.current()]
            if (permission != null) {
                try {
                    return evaluate(parts.next(), permission, definitionInstance)
                } catch (ex: UnknownPermissionException) {
                    throw UnknownPermissionException(parts.fullId, parts.current(), ex)
                }
            }

        }

        val childDefinition = definitionInstance.definition.definitions[parts.current()]
        if (childDefinition != null) {
            return evaluate(parts.next(), childDefinition, definitionInstance)
        }


        throw UnknownPermissionException(parts.fullId, parts.current())
    }

    private fun evaluate(parts: PermissionParts, permission: Permission, definitionInstance: DefinitionInstance): Boolean {
        return permission.includedIn.map {
            val resolved = definitionInstance.resolveDefinitionInstance(it.definitionName)
                ?: error("Cannot resolve definition '${it.definitionName}' from ${parts.fullId}")
            resolved.toString() + "/" + it.permissionName
        }.any { hasPermission(it) }
    }

    protected abstract fun getPermissionIdsFromInput(): List<String>

    inner class DefinitionInstance(
        val parent: DefinitionInstance?,
        val definition: Definition,
        val parameterValues: List<String>,
    ) {
        fun resolveDefinitionInstance(name: String): DefinitionInstance? {
            if (definition.name == name) return this

            // TODO schema.relations.filter {  }

            return parent?.resolveDefinitionInstance(name)
        }

        override fun toString(): String {
            return (listOfNotNull(parent?.toString()) + definition.name + parameterValues).joinToString("/")
        }
    }
}

class DefaultPermissionEvaluator(schema: Schema<DefaultAuthorizationInput>, input: DefaultAuthorizationInput) : PermissionEvaluator<DefaultAuthorizationInput>(schema, input) {
    override fun getPermissionIdsFromInput(): List<String> {
        return (input.jwt["permissions"] as? JsonArray)?.toList()
            ?.mapNotNull { (it as? JsonPrimitive)?.contentOrNull }
            ?: emptyList()
    }
}

class SchemaBuilder<Input> {
    private val inheritedSchemas: MutableList<Schema<Input>> = mutableListOf()
    private val definitionBuilders: MutableMap<String, DefinitionBuilder> = mutableMapOf()
    private val relationBuilders: MutableList<RelationBuilder> = mutableListOf()

    fun extends(schema: Schema<Input>) = also {
        inheritedSchemas += schema
    }

    fun build() = Schema<Input>(
        definitions = definitionBuilders.mapValues { it.value.build() },
        relations = relationBuilders.map { it.build() }
    )

    fun policy(name: String, body: PolicyBuilder.() -> Unit) {
        TODO()
    }

    fun definition(name: String, body: DefinitionBuilder.() -> Unit = {}) {
        definitionBuilders[name] = DefinitionBuilder(name).also(body)
    }

    fun relation(body: RelationBuilder.() -> Unit = {}): RelationBuilder {
        return RelationBuilder().also { relationBuilders += it }.also(body)
    }

    inner class DefinitionBuilder(private val definitionName: String) {
        private val permissionBuilders: MutableMap<String, PermissionBuilder> = HashMap()
        private val parameters: MutableMap<String, DefinitionParameter<*>> = HashMap()
        private val innerDefinitionBuilders: MutableMap<String, DefinitionBuilder> = mutableMapOf()
        fun build(): Definition = Definition(
            definitionName,
            parameters.map { it.value.name },
            innerDefinitionBuilders.mapValues { it.value.build() },
            permissionBuilders.mapValues { it.value.build() }
        )
        fun definition(name: String, body: DefinitionBuilder.() -> Unit = {}) {
            innerDefinitionBuilders[name] = DefinitionBuilder(name).also(body)
        }
        fun relation(name: String, body: RelationBuilder.() -> Unit = {}): RelationBuilder {
            return RelationBuilder().also { relationBuilders += it }.also(body)
        }

        fun permission(name: String, body: PermissionBuilder.() -> Unit = {}): PermissionBuilder {
            return permissionBuilders.getOrPut(name) { PermissionBuilder(name) }.also(body)
        }

        fun <T> parameter(name: String): DefinitionParameter<T> {
            return DefinitionParameter<T>(name).also { parameters[name] = it }
        }

        fun <T> existingParameter(name: String): DefinitionParameter<T> {
            return checkNotNull(parameters[name]) { "Parameter not found: $name" } as DefinitionParameter<T>
        }

        inner class PermissionBuilder(private val permissionName: String) {
            private var description: String? = null
            private val includedIn: MutableList<PermissionReference> = ArrayList()

            fun build(): Permission {
                return Permission(permissionName, includedIn)
            }

            fun permission(name: String, body: PermissionBuilder.() -> Unit = {}) {
                this@DefinitionBuilder.permission(name, body).also { it.includedIn(definitionName, permissionName) }
            }

            fun description(newDescription: String) {
                description = newDescription
            }
            fun includedIn(permissionName: String) = includedIn(definitionName, permissionName)
            fun includedIn(definitionName: String, permissionName: String) {
                includedIn += PermissionReference(definitionName, permissionName)
            }
            fun grantIf(condition: IPermissionContext<Input>.() -> Boolean) {  }
        }
    }

    inner class RelationBuilder {
        private var fromDefinition: String? = null
        private var fromRole: String? = null
        private var toDefinition: String? = null
        private var toRole: String? = null
        fun build() = Relation()

        fun from(definition: String, role: String) = also {
            this.fromDefinition = definition
            this.fromRole = role
        }

        fun to(definition: String, role: String) = also {
            this.toDefinition = definition
            this.toRole = role
        }

        fun target(definition: String, body: TargetBuilder.() -> Unit) {  }
        inner class TargetBuilder {
            fun <T : Any> parameterValue(parameterName: String, parameterValue: IDefinitionContext<Input>.() -> T?) {  }
            fun role(role: String) { TODO() }
        }
    }

    inner class PolicyBuilder {

    }

}

class DefaultAuthorizationInput(val jwt: JsonObject) {

}

interface IDefinitionContext<Input> {
    val input: Input
    fun <T> DefinitionParameter<T>.get(): T
}

interface IPermissionContext<Input> {
    val input: Input
    val permissionId: String
    fun <T> DefinitionParameter<T>.get(): T
}

class DefinitionParameter<E>(val name: String)

class PermissionReference(val definitionName: String, val permissionName: String)

fun <Input> buildSchema(body: SchemaBuilder<Input>.() -> Unit): Schema<Input> {
    return SchemaBuilder<Input>().also(body).build()
}

fun buildSchemaForDefaultInput(body: SchemaBuilder<DefaultAuthorizationInput>.() -> Unit): Schema<DefaultAuthorizationInput> {
    return buildSchema(body)
}

val baseSchema = buildSchemaForDefaultInput {
    definition("group") {
        relation("directMembers")
    }
    definition("user") {

    }
}

val modelServerSchema = buildSchemaForDefaultInput {
    extends(baseSchema)
    definition("repository") {
        parameter<String>("name")

        permission("admin") {
            permission("rewrite")
        }

        permission("rewrite") {
            permission("delete")
            permission("write") {
                permission("create")
                permission("read") {
                    permission("list") {
                        includedIn("branch", "list")
                    }
                }
            }
        }

        definition("objects") {
            permission("read") {
                includedIn("branch", "pull")
            }
        }

        definition("branch") {
            parameter<String>("name")

            relation("repository").to("repository", "branches")

            permission("admin") {
                permission("rewrite") {
                    includedIn("repository", "rewrite")
                    description("Destructive write operations that change the history and loses previously pushed changes.")

                    permission("force-push") {
                        description("Overwrite the current version. Don't do any merges and don't prevent losing history.")
                    }
                    permission("delete")
                    permission("write") {
                        description("Non-destructive write operations that preserve the history.")
                        includedIn("repository", "write")
                        permission("create") {
                            description("Can create a branch with this name, if it doesn't exist yet.")
                        }
                        permission("push") {
                            description("Add changes to a branch and merge it with the current version ")
                        }
                        permission("read") {
                            permission("list") {
                                description("Allowed to know its existence and name, but not the content.")
                            }
                            permission("pull") {
                                description("Allowed reading the version hash. Permissions on objects is checked on repository level, which mean if a client knows the hash it can still read the content.")
                            }
                        }
                    }
                }
            }

        }
    }

}

/*val workspacesSchema = buildSchemaForDefaultInput {
    extends(modelServerSchema)
    val repositoryNamePrefix = "workspace-"
    definition("workspace") {
        val workspaceId = parameter<Long>("id")
        relation("repository") {
            target("repository") {
                role("owning-workspace")
                parameterValue("name") { repositoryNamePrefix + workspaceId.get().toString(16) }

            }
        }
    }

    definition("repository") {
        val repositoryName = existingParameter<String>("name")
        relation("owning-workspace") {
            target("workspace") {
                parameterValue<Long>("id") { repositoryName.get().substringAfter(repositoryNamePrefix).takeIf { repositoryName.get().startsWith(repositoryNamePrefix) }?.toLong(16) }
            }
        }
    }
}*/


