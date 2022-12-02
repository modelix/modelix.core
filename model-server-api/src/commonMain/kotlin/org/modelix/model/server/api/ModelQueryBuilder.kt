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


class ModelQueryBuilder : QueryOwnerBuilder() {
    fun build() = ModelQuery(subqueries)
}

abstract class QueryOwnerBuilder {
    protected val subqueries = ArrayList<Subquery>()
    protected val filters = ArrayList<Filter>()
    protected fun addSubquery(sq: Subquery) {
        subqueries.add(sq)
    }
    protected fun addFilter(filter: Filter) {
        filters.add(filter)
    }
    fun root(body: RootNodeBuilder.() -> Unit) {
        addSubquery(RootNodeBuilder().also(body).build())
    }
    fun children(role: String?, body: ChildrenBuilder.() -> Unit) {
        addSubquery(ChildrenBuilder(role).also(body).build())
    }
    fun reference(role: String, body: ReferenceBuilder.() -> Unit) {
        addSubquery(ReferenceBuilder(role).also(body).build())
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
    fun resolve(nodeId: NodeId, body: ByIdBuilder.() -> Unit = {}) {
        addSubquery(ByIdBuilder(nodeId).also(body).build())
    }
    fun whereProperty(role: String) = StringFilterBuilder { FilterByProperty(role, it) }
    fun whereConceptName() = StringFilterBuilder { FilterByConceptLongName(it) }

    fun whereConcept(conceptUID: String?) {
        addFilter(FilterByConceptId(conceptUID))
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

sealed class SubqueryBuilder : QueryOwnerBuilder() {
    abstract fun build(): Subquery
}

class RootNodeBuilder() : SubqueryBuilder() {
    override fun build() = QueryRootNode(subqueries, filters)
}

class ByIdBuilder(val id: String) : SubqueryBuilder() {
    override fun build() = QueryById(id, subqueries, filters)
}

class ChildrenBuilder(val role: String?) : SubqueryBuilder() {
    override fun build() = QueryChildren(role, subqueries, filters)
}

class ReferenceBuilder(val role: String) : SubqueryBuilder() {
    override fun build() = QueryReference(role, subqueries, filters)
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
            whereProperty("name").isNotNull()
            whereConcept(null)
            descendants {
                whereConcept(null)
            }
        }
        reference("target") {

        }
    }
}