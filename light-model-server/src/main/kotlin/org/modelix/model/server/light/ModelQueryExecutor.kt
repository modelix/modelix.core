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

package org.modelix.model.server.light

import org.modelix.model.api.INode
import org.modelix.model.api.INodeReferenceSerializer
import org.modelix.model.api.getAncestors
import org.modelix.model.api.getDescendants
import org.modelix.model.server.api.AndFilter
import org.modelix.model.server.api.ContainsOperator
import org.modelix.model.server.api.EndsWithOperator
import org.modelix.model.server.api.EqualsOperator
import org.modelix.model.server.api.Filter
import org.modelix.model.server.api.FilterByConceptId
import org.modelix.model.server.api.FilterByConceptLongName
import org.modelix.model.server.api.FilterByProperty
import org.modelix.model.server.api.IsNotNullOperator
import org.modelix.model.server.api.IsNullOperator
import org.modelix.model.server.api.MatchesRegexOperator
import org.modelix.model.server.api.ModelQuery
import org.modelix.model.server.api.OrFilter
import org.modelix.model.server.api.QueryAllChildren
import org.modelix.model.server.api.QueryAncestors
import org.modelix.model.server.api.QueryById
import org.modelix.model.server.api.QueryChildren
import org.modelix.model.server.api.QueryDescendants
import org.modelix.model.server.api.QueryParent
import org.modelix.model.server.api.QueryReference
import org.modelix.model.server.api.QueryRootNode
import org.modelix.model.server.api.RootOrSubquery
import org.modelix.model.server.api.StartsWithOperator
import org.modelix.model.server.api.StringOperator
import org.modelix.model.server.api.Subquery

private class ModelQueryExecutor(val rootNode: INode) {
    private val area = rootNode.getArea()
    private val includedNodes: MutableSet<INode> = HashSet()

    fun execute(query: ModelQuery): Set<INode> {
        query.queries.forEach { execute(rootNode, it) }
        return includedNodes
    }

    private fun execute(contextNode: INode, query: RootOrSubquery) {
        when (query) {
            is QueryAllChildren -> {
                contextNode.allChildren.forEach { visitNode(it, query) }
            }
            is QueryAncestors -> {
                contextNode.getAncestors(false).forEach { visitNode(it, query) }
            }
            is QueryById -> {
                val resolved = INodeReferenceSerializer.deserialize(query.nodeId).resolveNode(area)
                if (resolved != null) {
                    visitNode(resolved, query)
                }
            }
            is QueryChildren -> {
                contextNode.getChildren(query.role).forEach { visitNode(it, query) }
            }
            is QueryDescendants -> {
                contextNode.getDescendants(false).forEach { visitNode(it, query) }
            }
            is QueryParent -> contextNode.parent?.let { visitNode(it, query) }
            is QueryReference -> {
                contextNode.getReferenceTarget(query.role)?.let { visitNode(it, query) }
            }
            is QueryRootNode -> visitNode(rootNode, query)
        }
    }

    private fun visitNode(node: INode, query: RootOrSubquery) {
        if (query is Subquery) {
            for (filter in query.filters) {
                if (!applyFilter(node, filter)) return
            }
        }
        includedNodes.add(node)
        for (childQuery in query.queries) {
            execute(node, childQuery)
        }
    }

    private fun applyFilter(node: INode, filter: Filter): Boolean {
        return when (filter) {
            is AndFilter -> filter.filters.all { applyFilter(node, it) }
            is OrFilter -> filter.filters.isEmpty() || filter.filters.any { applyFilter(node, it) }
            is FilterByConceptId -> node.getConceptReference()?.getUID() == filter.conceptUID
            is FilterByProperty -> applyStringOperator(node.getPropertyValue(filter.role), filter.operator)
            is FilterByConceptLongName -> applyStringOperator(node.concept?.getLongName(), filter.operator)
        }
    }

    private fun applyStringOperator(value: String?, operator: StringOperator): Boolean {
        return when (operator) {
            is ContainsOperator -> value?.contains(operator.substring) ?: false
            is EndsWithOperator -> value?.endsWith(operator.suffix) ?: false
            is EqualsOperator -> value == operator.value
            is IsNotNullOperator -> value != null
            is IsNullOperator -> value == null
            is MatchesRegexOperator -> value?.matches(Regex(operator.pattern)) ?: false
            is StartsWithOperator -> value?.startsWith(operator.prefix) ?: false
        }
    }
}

fun ModelQuery.execute(rootNode: INode): Set<INode> {
    return ModelQueryExecutor(rootNode).execute(this)
}