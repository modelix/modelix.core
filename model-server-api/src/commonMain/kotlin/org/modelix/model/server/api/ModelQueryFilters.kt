package org.modelix.model.server.api

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable


@Serializable
sealed class Filter

sealed class LogicalOperatorFilter : Filter() {
    abstract val filters: List<Filter>
}

@Serializable
@SerialName("or")
data class OrFilter(override val filters: List<Filter>) : LogicalOperatorFilter()
infix fun Filter.or(other: Filter): Filter = OrFilter(listOf(this, other))

@Serializable
@SerialName("and")
data class AndFilter(override val filters: List<Filter>) : LogicalOperatorFilter()
infix fun Filter.and(other: Filter): Filter = AndFilter(listOf(this, other))

@Serializable
@SerialName("not")
data class NotFilter(val filter: Filter) : Filter()
operator fun Filter.not(): NotFilter = NotFilter(this)


@Serializable
@SerialName("conceptId")
data class FilterByConceptId(val conceptUID: String?) : Filter()

@Serializable
@SerialName("conceptName")
data class FilterByConceptLongName(val operator: StringOperator) : Filter()

@Serializable
@SerialName("property")
class FilterByProperty(val role: String, val operator: StringOperator) : Filter()
interface IPropertyFilterOperand {
    fun createFilter(operator: StringOperator): Filter
}
@Serializable
sealed class StringOperator

@Serializable
@SerialName("equals")
data class EqualsOperator(val value: String) : StringOperator()
fun IPropertyFilterOperand.equalTo(value: String): Filter = createFilter(EqualsOperator(value))

@Serializable
@SerialName("startsWith")
data class StartsWithOperator(val prefix: String) : StringOperator()
fun IPropertyFilterOperand.startsWith(prefix: String): Filter = createFilter(StartsWithOperator(prefix))

@Serializable
@SerialName("endsWith")
data class EndsWithOperator(val suffix: String) : StringOperator()
fun IPropertyFilterOperand.endsWith(suffix: String): Filter = createFilter(EndsWithOperator(suffix))

@Serializable
@SerialName("contains")
data class ContainsOperator(val substring: String) : StringOperator()
fun IPropertyFilterOperand.contains(substring: String): Filter = createFilter(ContainsOperator(substring))

@Serializable
@SerialName("regex")
data class MatchesRegexOperator(val pattern: String) : StringOperator()
fun IPropertyFilterOperand.matches(regex: Regex): Filter = createFilter(MatchesRegexOperator(regex.pattern))

@Serializable
@SerialName("isNotNull")
object IsNotNullOperator : StringOperator()
fun IPropertyFilterOperand.isNotNull(): Filter = createFilter(IsNotNullOperator)

@Serializable
@SerialName("isNull")
object IsNullOperator : StringOperator()
fun IPropertyFilterOperand.isNull(): Filter = createFilter(IsNullOperator)

