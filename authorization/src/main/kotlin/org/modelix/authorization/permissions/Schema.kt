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

import kotlinx.serialization.Serializable

/**
 * An abstract description of the available permissions and the relation between them.
 */
@Serializable
data class Schema(
    val resources: Map<String, Resource>,
    val relations: List<Relation>,
) {
    fun findResource(name: String): Resource {
        return requireNotNull(resources.values.asSequence().mapNotNull { it.findResource(name) }.firstOrNull()) { "Schema not found: $name" }
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
data class Relation(
    val fromResource: String,
    val fromRole: String,
    val toResource: String,
    val toRole: String?,
    val targetParameterValues: Map<String, IExpression>,
)

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
