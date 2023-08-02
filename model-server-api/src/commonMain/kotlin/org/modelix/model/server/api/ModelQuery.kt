/*
 * Copyright (c) 2022.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *         http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.modelix.model.server.api

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

@Serializable
sealed class QueryOwner {
    abstract val queries: List<RootOrSubquery>
}

@Serializable
data class ModelQuery(
    override val queries: List<RootQuery>
) : QueryOwner() {
    fun toJson() = Json.encodeToString(this)
    companion object {
        fun fromJson(json: String) = Json.decodeFromString<ModelQuery>(json)
    }
}

sealed class RootOrSubquery : QueryOwner() {
    abstract override val queries: List<Subquery>
}

@Serializable
sealed class Subquery() : RootOrSubquery() {
    abstract override val queries: List<Subquery>
    abstract val filters: List<Filter>
}

@Serializable
sealed class RootQuery : RootOrSubquery() {
    abstract override val queries: List<Subquery>
}

@Serializable
@SerialName("resolve")
data class QueryById(
    val nodeId: NodeId,
    override val queries: List<Subquery> = emptyList()
) : RootQuery()

@Serializable
@SerialName("root")
data class QueryRootNode(
    override val queries: List<Subquery> = emptyList()
) : RootQuery()

@Serializable
@SerialName("allChildren")
data class QueryAllChildren(
    override val queries: List<Subquery> = emptyList(),
    override val filters: List<Filter> = emptyList()
) : Subquery()

@Serializable
@SerialName("children")
data class QueryChildren(
    val role: String?,
    override val queries: List<Subquery> = emptyList(),
    override val filters: List<Filter> = emptyList()
) : Subquery()

@Serializable
@SerialName("reference")
data class QueryReference(
    val role: String,
    override val queries: List<Subquery> = emptyList(),
    override val filters: List<Filter> = emptyList()
) : Subquery()

@Serializable
@SerialName("references")
data class QueryReferences(
    override val queries: List<Subquery> = emptyList(),
    override val filters: List<Filter> = emptyList()
) : Subquery()

@Serializable
@SerialName("referencesAndChildren")
data class QueryReferencesAndChildren(
    val recursive: Boolean = false,
    override val queries: List<Subquery> = emptyList(),
    override val filters: List<Filter> = emptyList()
) : Subquery()

@Serializable
@SerialName("descendants")
data class QueryDescendants(
    override val queries: List<Subquery> = emptyList(),
    override val filters: List<Filter> = emptyList()
) : Subquery()

@Serializable
@SerialName("ancestors")
data class QueryAncestors(
    override val queries: List<Subquery> = emptyList(),
    override val filters: List<Filter> = emptyList()
) : Subquery()

@Serializable
@SerialName("parent")
data class QueryParent(
    override val queries: List<Subquery> = emptyList(),
    override val filters: List<Filter> = emptyList()
) : Subquery()
