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

@Serializable
sealed class QueryOwner {
    abstract val queries: List<Subquery>
}

@Serializable
data class ModelQuery(
    override val queries: List<Subquery>
) : QueryOwner()

@Serializable
sealed class Subquery() : QueryOwner() {
    abstract val filters: List<Filter>
}

@Serializable
@SerialName("resolve")
data class QueryById(
    val nodeId: NodeId,
    override val queries: List<Subquery> = emptyList(),
    override val filters: List<Filter> = emptyList()
) : Subquery()

@Serializable
@SerialName("root")
data class QueryRootNode(
    override val queries: List<Subquery> = emptyList(),
    override val filters: List<Filter> = emptyList()
) : Subquery()

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

@Serializable
sealed class Filter

@Serializable
@SerialName("property")
sealed class FilterByProperty : Filter() {
    abstract val role: String
}

@Serializable
@SerialName("concept")
data class FilterByConcept(val conceptUID: String?) : Filter()

@Serializable
@SerialName("property-equals")
data class PropertyEquals(override val role: String, val value: String) : FilterByProperty()
@Serializable
@SerialName("property-startsWith")
data class PropertyStartsWith(override val role: String, val prefix: String) : FilterByProperty()
@Serializable
@SerialName("property-endsWith")
data class PropertyEndWith(override val role: String, val suffix: String) : FilterByProperty()
@Serializable
@SerialName("property-contains")
data class PropertyContains(override val role: String, val substring: String) : FilterByProperty()
@Serializable
@SerialName("property-regex")
data class PropertyMatchesRegex(override val role: String, val pattern: String) : FilterByProperty()
@Serializable
@SerialName("property-isNotNull")
data class PropertyIsNotNull(override val role: String) : FilterByProperty()
@Serializable
@SerialName("property-isNull")
data class PropertyIsNull(override val role: String) : FilterByProperty()

