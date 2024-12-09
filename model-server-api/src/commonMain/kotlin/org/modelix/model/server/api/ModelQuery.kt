package org.modelix.model.server.api

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

@Serializable
sealed class QueryOwner {
    abstract val queries: List<RootOrSubquery>
}

@Serializable
data class ModelQuery(
    override val queries: List<RootQuery>,
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
    override val queries: List<Subquery> = emptyList(),
) : RootQuery()

@Serializable
@SerialName("root")
data class QueryRootNode(
    override val queries: List<Subquery> = emptyList(),
) : RootQuery()

@Serializable
@SerialName("allChildren")
data class QueryAllChildren(
    override val queries: List<Subquery> = emptyList(),
    override val filters: List<Filter> = emptyList(),
) : Subquery()

@Serializable
@SerialName("children")
data class QueryChildren(
    val role: String?,
    override val queries: List<Subquery> = emptyList(),
    override val filters: List<Filter> = emptyList(),
) : Subquery()

@Serializable
@SerialName("reference")
data class QueryReference(
    val role: String,
    override val queries: List<Subquery> = emptyList(),
    override val filters: List<Filter> = emptyList(),
) : Subquery()

@Serializable
@SerialName("references")
data class QueryReferences(
    override val queries: List<Subquery> = emptyList(),
    override val filters: List<Filter> = emptyList(),
) : Subquery()

@Serializable
@SerialName("referencesAndChildren")
data class QueryReferencesAndChildren(
    val recursive: Boolean = false,
    override val queries: List<Subquery> = emptyList(),
    override val filters: List<Filter> = emptyList(),
) : Subquery()

@Serializable
@SerialName("descendants")
data class QueryDescendants(
    override val queries: List<Subquery> = emptyList(),
    override val filters: List<Filter> = emptyList(),
) : Subquery()

@Serializable
@SerialName("ancestors")
data class QueryAncestors(
    override val queries: List<Subquery> = emptyList(),
    override val filters: List<Filter> = emptyList(),
) : Subquery()

@Serializable
@SerialName("parent")
data class QueryParent(
    override val queries: List<Subquery> = emptyList(),
    override val filters: List<Filter> = emptyList(),
) : Subquery()

@Serializable
sealed class Filter

sealed class LogicalOperatorFilter : Filter() {
    abstract val filters: List<Filter>
}

@Serializable
@SerialName("or")
data class OrFilter(override val filters: List<Filter>) : LogicalOperatorFilter()

@Serializable
@SerialName("and")
data class AndFilter(override val filters: List<Filter>) : LogicalOperatorFilter()

@Serializable
@SerialName("not")
data class NotFilter(val filter: Filter) : Filter()

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
