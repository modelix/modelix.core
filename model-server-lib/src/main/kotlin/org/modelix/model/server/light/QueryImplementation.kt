/*
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
package org.modelix.model.server.light

import org.modelix.model.api.*
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
import org.modelix.model.server.api.NotFilter
import org.modelix.model.server.api.OrFilter
import org.modelix.model.server.api.QueryAllChildren
import org.modelix.model.server.api.QueryAncestors
import org.modelix.model.server.api.QueryById
import org.modelix.model.server.api.QueryChildren
import org.modelix.model.server.api.QueryDescendants
import org.modelix.model.server.api.QueryParent
import org.modelix.model.server.api.QueryReference
import org.modelix.model.server.api.QueryReferences
import org.modelix.model.server.api.QueryReferencesAndChildren
import org.modelix.model.server.api.QueryRootNode
import org.modelix.model.server.api.RootOrSubquery
import org.modelix.model.server.api.RootQuery
import org.modelix.model.server.api.StartsWithOperator
import org.modelix.model.server.api.StringOperator
import org.modelix.model.server.api.Subquery

fun RootOrSubquery.queryNodes(node: INode): Sequence<INode> {
    return when (this) {
        is QueryAllChildren -> node.allChildren.asSequence()
        is QueryAncestors -> node.getAncestors(false)
        is QueryChildren -> try {
            node.getChildren(this.role).asSequence()
        } catch (e: RuntimeException) {
            emptySequence()
        }
        is QueryDescendants -> node.getDescendants(false)
        is QueryParent -> listOfNotNull(node.parent).asSequence()
        is QueryReference -> {
            val link = try {
                node.resolveReferenceLink(this.role)
            } catch (ex: Exception) {
                null
            }
            if (link == null) {
                emptySequence()
            } else {
                listOfNotNull(node.getReferenceTarget(link)).asSequence()
            }
        }
        is QueryReferences -> node.getAllReferences()
        is QueryReferencesAndChildren -> node.getReferencesAndChildren(recursive)
        is QueryById -> {
            val resolved = INodeReferenceSerializer.deserialize(this.nodeId).resolveIn(node.getArea()!!)
            if (resolved != null) {
                sequenceOf(resolved)
            } else {
                emptySequence()
            }
        }
        is QueryRootNode -> {
            sequenceOf(node)
        }
    }
}

private fun INode.getAllReferences() = getReferenceRoles().asSequence().mapNotNull { getReferenceTarget(it) }

private fun INode.getReferencesAndChildren(recursive: Boolean): Sequence<INode> {
    val referencesAndChildren = (getAllReferences() + allChildren.asSequence())
    return if (recursive) {
        referencesAndChildren.flatMap { sequenceOf(it) + it.getReferencesAndChildren(true) }
    } else {
        referencesAndChildren
    }
}

fun RootOrSubquery.applyFilters(node: INode): Boolean {
    return when (this) {
        is RootQuery -> true
        is Subquery -> this.filters.all { it.apply(node) }
    }
}

fun Filter.apply(node: INode): Boolean {
    // When adding new types of filters the invalidation algorithm might be adjusted if the filter has
    // dependencies outside the node.
    return when (this) {
        is NotFilter -> !filter.apply(node)
        is AndFilter -> filters.all { it.apply(node) }
        is OrFilter -> filters.isEmpty() || filters.any { it.apply(node) }
        is FilterByConceptId -> node.getConceptReference()?.getUID() == conceptUID
        is FilterByProperty -> operator.apply(node.getPropertyValue(role))
        is FilterByConceptLongName -> operator.apply(node.concept?.getLongName())
    }
}

fun StringOperator.apply(value: String?): Boolean {
    return when (this) {
        is ContainsOperator -> value?.contains(substring) ?: false
        is EndsWithOperator -> value?.endsWith(suffix) ?: false
        is EqualsOperator -> value == this.value
        is IsNotNullOperator -> value != null
        is IsNullOperator -> value == null
        is MatchesRegexOperator -> value?.matches(Regex(pattern)) ?: false
        is StartsWithOperator -> value?.startsWith(prefix) ?: false
    }
}