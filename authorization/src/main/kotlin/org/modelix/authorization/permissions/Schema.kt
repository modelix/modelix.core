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
    val definitions: Map<String, Definition>,
    val relations: List<Relation>
)

@Serializable
data class Definition(
    val name: String,
    val parameters: List<String>,
    val definitions: Map<String, Definition>,
    val permissions: Map<String, Permission>
)

@Serializable
data class Relation(
    val fromDefinition: String,
    val fromRole: String?,
    val toDefinition: String,
    val toRole: String?
)

@Serializable
data class Permission(
    val name: String,
    val description: String? = null,
    val includedIn: List<ScopedPermissionName>
)

@Serializable
data class ScopedPermissionName(val definitionName: String, val permissionName: String)
