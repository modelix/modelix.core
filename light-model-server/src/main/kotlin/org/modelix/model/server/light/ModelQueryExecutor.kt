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
import org.modelix.model.server.api.Filter
import org.modelix.model.server.api.FilterByConcept
import org.modelix.model.server.api.FilterByProperty
import org.modelix.model.server.api.ModelQuery
import org.modelix.model.server.api.PropertyContains
import org.modelix.model.server.api.PropertyEndWith
import org.modelix.model.server.api.PropertyEquals
import org.modelix.model.server.api.PropertyIsNotNull
import org.modelix.model.server.api.PropertyIsNull
import org.modelix.model.server.api.PropertyMatchesRegex
import org.modelix.model.server.api.PropertyStartsWith
import org.modelix.model.server.api.QueryAllChildren
import org.modelix.model.server.api.QueryAncestors
import org.modelix.model.server.api.QueryById
import org.modelix.model.server.api.QueryChildren
import org.modelix.model.server.api.QueryDescendants
import org.modelix.model.server.api.QueryParent
import org.modelix.model.server.api.QueryReference
import org.modelix.model.server.api.QueryRootNode
import org.modelix.model.server.api.Subquery

private class ModelQueryExecutor(val rootNode: INode) {
    private val area = rootNode.getArea()
    private val includedNodes: MutableSet<INode> = HashSet()

    fun execute(query: ModelQuery): Set<INode> {
        query.queries.forEach { execute(rootNode, it) }
        return includedNodes
    }

    private fun execute(contextNode: INode, query: Subquery) {
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

    private fun visitNode(node: INode, query: Subquery) {
        for (filter in query.filters) {
            if (!applyFilter(node, filter)) return
        }
        includedNodes.add(node)
        for (childQuery in query.queries) {
            execute(node, childQuery)
        }
    }

    private fun applyFilter(node: INode, filter: Filter): Boolean {
        return when (filter) {
            is FilterByConcept -> node.getConceptReference()?.getUID() == filter.conceptUID
            is FilterByProperty -> {
                val value = node.getPropertyValue(filter.role)
                when (filter) {
                    is PropertyContains -> value?.contains(filter.substring) ?: false
                    is PropertyEndWith -> value?.endsWith(filter.suffix) ?: false
                    is PropertyEquals -> value == filter.value
                    is PropertyIsNotNull -> value != null
                    is PropertyIsNull -> value == null
                    is PropertyMatchesRegex -> value?.matches(Regex(filter.pattern)) ?: false
                    is PropertyStartsWith -> value?.startsWith(filter.prefix) ?: false
                }
            }
        }
    }
}

fun ModelQuery.execute(rootNode: INode): Set<INode> {
    return ModelQueryExecutor(rootNode).execute(this)
}