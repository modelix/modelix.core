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
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

@Serializable
sealed class QueryOwner {
    abstract val queries: List<Subquery>
}

@Serializable
data class ModelQuery(
    override val queries: List<Subquery>
) : QueryOwner() {
    fun toJson() = Json.encodeToString(this)
}

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
@SerialName("conceptId")
data class FilterByConceptId(val conceptUID: String?) : Filter()
@Serializable
@SerialName("conceptName")
data class FilterByConceptLongName(val operator: StringOperator) : Filter()

@Serializable
@SerialName("property")
class FilterByProperty(val role: String, val operator: StringOperator) : Filter()
@Serializable
sealed class StringOperator

@Serializable
@SerialName("equals")
data class EqualsOperator(val value: String) : StringOperator()
@Serializable
@SerialName("startsWith")
data class StartsWithOperator(val prefix: String) : StringOperator()
@Serializable
@SerialName("endsWith")
data class EndsWithOperator(val suffix: String) : StringOperator()
@Serializable
@SerialName("contains")
data class ContainsOperator(val substring: String) : StringOperator()
@Serializable
@SerialName("regex")
data class MatchesRegexOperator(val pattern: String) : StringOperator()
@Serializable
@SerialName("isNotNull")
object IsNotNullOperator : StringOperator()
@Serializable
@SerialName("isNull")
object IsNullOperator : StringOperator()

