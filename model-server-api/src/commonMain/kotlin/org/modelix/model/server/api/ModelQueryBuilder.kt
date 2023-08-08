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

fun buildModelQuery(body: ModelQueryBuilder.() -> Unit): ModelQuery {
    return ModelQueryBuilder().also(body).build()
}

class ModelQueryBuilder {
    private val rootQueries = ArrayList<RootQuery>()
    fun build() = ModelQuery(rootQueries)
    fun root(body: RootNodeBuilder.() -> Unit) {
        rootQueries += RootNodeBuilder().also(body).build()
    }
    fun resolve(nodeId: NodeId, body: ByIdBuilder.() -> Unit = {}) {
        rootQueries += ByIdBuilder(nodeId).also(body).build()
    }
}

open class FilterListBuilder {
    protected val filters = ArrayList<Filter>()

    protected fun addFilter(filter: Filter) {
        filters.add(filter)
    }

    fun whereProperty(role: String) = StringFilterBuilder { FilterByProperty(role, it) }

    fun whereConceptName() = StringFilterBuilder { FilterByConceptLongName(it) }

    fun whereConcept(conceptUID: String?) {
        addFilter(FilterByConceptId(conceptUID))
    }

    fun and(body: FilterListBuilder.() -> Unit) {
        addFilter(AndFilter(FilterListBuilder().also(body).filters))
    }

    fun or(body: FilterListBuilder.() -> Unit) {
        addFilter(OrFilter(FilterListBuilder().also(body).filters))
    }

    fun not(body: FilterListBuilder.() -> Unit) {
        val childFilters = FilterListBuilder().also(body).filters
        addFilter(if (childFilters.size == 1) NotFilter(childFilters.first()) else NotFilter(AndFilter(childFilters)))
    }

    inner class StringFilterBuilder(val filterBuilder: (StringOperator) -> Filter) {
        fun startsWith(prefix: String) { addFilter(filterBuilder(StartsWithOperator(prefix))) }
        fun endsWith(suffix: String) { addFilter(filterBuilder(EndsWithOperator(suffix))) }
        fun contains(substring: String) { addFilter(filterBuilder(ContainsOperator(substring))) }
        fun equalTo(value: String) { addFilter(filterBuilder(EqualsOperator(value))) }
        fun matches(pattern: Regex) { addFilter(filterBuilder(MatchesRegexOperator(pattern.pattern))) }
        fun isNotNull() { addFilter(filterBuilder(IsNotNullOperator)) }
        fun isNull() { addFilter(filterBuilder(IsNullOperator)) }
    }
}

abstract class QueryOwnerBuilder : FilterListBuilder() {
    protected val subqueries = ArrayList<Subquery>()
    protected fun addSubquery(sq: Subquery) {
        subqueries.add(sq)
    }

    fun children(role: String?, body: ChildrenBuilder.() -> Unit) {
        addSubquery(ChildrenBuilder(role).also(body).build())
    }
    fun reference(role: String, body: ReferenceBuilder.() -> Unit) {
        addSubquery(ReferenceBuilder(role).also(body).build())
    }
    fun references(body: ReferencesBuilder.() -> Unit) {
        addSubquery(ReferencesBuilder().also(body).build())
    }
    fun referencesAndChildren(body: ReferencesAndChildrenBuilder.() -> Unit) {
        addSubquery(ReferencesAndChildrenBuilder(false).also(body).build())
    }
    fun referencesAndChildrenRecursive(body: ReferencesAndChildrenBuilder.() -> Unit) {
        addSubquery(ReferencesAndChildrenBuilder(true).also(body).build())
    }
    fun allChildren(body: AllChildrenBuilder.() -> Unit) {
        addSubquery(AllChildrenBuilder().also(body).build())
    }
    fun descendants(body: DescendantsBuilder.() -> Unit) {
        addSubquery(DescendantsBuilder().also(body).build())
    }
    fun ancestors(body: AncestorsBuilder.() -> Unit) {
        addSubquery(AncestorsBuilder().also(body).build())
    }
    fun parent(body: ParentBuilder.() -> Unit) {
        addSubquery(ParentBuilder().also(body).build())
    }
}

sealed class RootQueryBuilder : QueryOwnerBuilder() {
    abstract fun build(): RootQuery
}

sealed class SubqueryBuilder : QueryOwnerBuilder() {
    abstract fun build(): Subquery
}

class RootNodeBuilder() : RootQueryBuilder() {
    override fun build() = QueryRootNode(subqueries)
}

class ByIdBuilder(val id: String) : RootQueryBuilder() {
    override fun build() = QueryById(id, subqueries)
}

class ChildrenBuilder(val role: String?) : SubqueryBuilder() {
    override fun build() = QueryChildren(role, subqueries, filters)
}

class ReferenceBuilder(val role: String) : SubqueryBuilder() {
    override fun build() = QueryReference(role, subqueries, filters)
}
class ReferencesBuilder() : SubqueryBuilder() {
    override fun build() = QueryReferences(subqueries, filters)
}
class ReferencesAndChildrenBuilder(private val recursive: Boolean) : SubqueryBuilder() {
    override fun build() = QueryReferencesAndChildren(recursive, subqueries, filters)
}

class AllChildrenBuilder() : SubqueryBuilder() {
    override fun build() = QueryAllChildren(subqueries, filters)
}
class DescendantsBuilder() : SubqueryBuilder() {
    override fun build() = QueryDescendants(subqueries, filters)
}
class AncestorsBuilder() : SubqueryBuilder() {
    override fun build() = QueryAncestors(subqueries, filters)
}
class ParentBuilder() : SubqueryBuilder() {
    override fun build() = QueryParent(subqueries, filters)
}

private val sandbox = buildModelQuery {
    resolve("mps-module:5622e615-959d-4843-9df6-ef04ee578c18(org.modelix.model.mpsadapters)") {
        children("models") {
            or {
                and {
                    whereProperty("name").isNotNull()
                    whereConcept(null)
                }
            }
            descendants {
                whereConcept(null)
            }
        }
        reference("target") {
        }
    }
}
