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

import com.auth0.jwt.interfaces.DecodedJWT


data class Schema<Input>(
    val definitions: Map<String, Definition>,
    val relations: List<Relation>
)
data class Definition(
    val parameters: List<String>,
    val name: String,
    val definitions: Map<String, Definition>,
    val permissions: Map<String, Permission>
)
class Relation()
data class Permission(val name: String)

data class DefinitionInstance(
    val parent: DefinitionInstance?,
    val definition: Definition,
    val parameterValues: List<String>,
)

class PermissionEngine<Input>(val schema: Schema<Input>) {
    fun hasPermission(permissionReference: String, input: Input): Boolean {
        val parts = permissionReference.split('/')
        return evaluate(parts)
    }

    private fun evaluate(remainingParts: List<String>): Boolean {
        val definition = requireNotNull(schema.definitions[remainingParts.first()]) {
            "Unknown permission part: ${remainingParts.first()}"
        }
        evaluate(remainingParts.drop(1), definition, null)
    }

    private fun evaluate(remainingParts: List<String>, definition: Definition, parent: DefinitionInstance?): Boolean {
        val parameterValues = remainingParts.take(definition.parameters.size)
        val instance = DefinitionInstance(parent, definition, parameterValues)
        return evaluate(remainingParts.drop(parameterValues.size), instance)
    }

    private fun evaluate(remainingParts: List<String>, definitionInstance: DefinitionInstance): Boolean {
        val permission = definitionInstance.definition.permissions[remainingParts.first()]
        if (permission != null) {

        }

        val childDefinition = requireNotNull(definitionInstance.definition.definitions[remainingParts.first()]) {
            "Unknown permission part: ${remainingParts.first()}"
        }
        return evaluate(remainingParts.drop(1), childDefinition, definitionInstance)
    }

    private fun evaluate(permission: Permission, definitionInstance: DefinitionInstance) {

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
        definitions = inheritedSchemas.fold(emptyMap<String, Definition>()) { acc, it -> acc + it.definitions } + definitionBuilders.mapValues { it.value.build() },
        relations = relationBuilders.map { it.build() }
    )

    fun definition(name: String, body: DefinitionBuilder.() -> Unit = {}) {
        definitionBuilders[name] = DefinitionBuilder(name).also(body)
    }

    fun relation(body: RelationBuilder.() -> Unit = {}): RelationBuilder {
        return RelationBuilder().also { relationBuilders += it }.also(body)
    }

    inner class DefinitionBuilder(val name: String) {
        private val permissionBuilders: MutableMap<String, PermissionBuilder> = HashMap()
        fun build() = Definition(name)

        fun relation(name: String, body: RelationBuilder.() -> Unit = {}): RelationBuilder {
            return RelationBuilder().also { relationBuilders += it }.also(body)
        }

        fun permission(name: String, body: PermissionBuilder.() -> Unit = {}): PermissionBuilder {
            return permissionBuilders.getOrPut(name) { PermissionBuilder() }.also(body)
        }

        fun <T> parameter(name: String): DefinitionParameter<T> { TODO() }
        fun <T> existingParameter(name: String): DefinitionParameter<T> { TODO() }

        inner class PermissionBuilder {
            private var description: String? = null
            fun includes(permissionName: String) { TODO() }
            fun includes(definitionName: String, permissionName: String) { TODO() }
            fun description(newDescription: String) {
                description = newDescription
            }
            fun includedIn(permissionName: String): Unit = TODO()
            fun includedIn(definitionName: String, permissionName: String): Unit = TODO()
            fun grantIf(condition: IPermissionContext<Input>.() -> Boolean) { TODO() }
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

        fun target(definition: String, body: TargetBuilder.() -> Unit) { TODO() }
        inner class TargetBuilder {
            fun <T : Any> parameterValue(parameterName: String, parameterValue: IDefinitionContext<Input>.() -> T?) { TODO() }
            fun role(role: String) { TODO() }
        }
    }

}

class DefaultAuthorizationInput(val jwt: DecodedJWT) {

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
                    permission("list")
                }
            }
        }

        definition("branch") {
            parameter<String>("name")

            relation("repository").to("repository", "branches")

            permission("owner") {
                includes("rewrite")
            }

            permission("rewrite") {
                includedIn("repository", "admin")
                description("Destructive write operations that change the history and loses previously pushed changes.")

                permission("force-push") {
                    description("Overwrite the current version. Don't do any merges and don't prevent losing history.")
                }
                permission("delete")
                permission("write") {
                    description("Non-destructive write operations that preserve the history.")
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

val workspacesSchema = buildSchemaForDefaultInput {
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
}


