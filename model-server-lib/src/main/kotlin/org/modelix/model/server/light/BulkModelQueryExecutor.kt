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
import org.modelix.model.server.api.ModelQuery
import org.modelix.model.server.api.RootOrSubquery

private class BulkModelQueryExecutor(val rootNode: INode) {
    private val includedNodes: MutableSet<INode> = HashSet()

    fun execute(query: ModelQuery): Set<INode> {
        query.queries.forEach { execute(rootNode, it) }
        return includedNodes
    }

    private fun execute(contextNode: INode, query: RootOrSubquery) {
        query.queryNodes(contextNode).forEach { visitNode(it, query) }
    }

    private fun visitNode(node: INode, query: RootOrSubquery) {
        if (!query.applyFilters(node)) return
        includedNodes.add(node)
        for (childQuery in query.queries) {
            execute(node, childQuery)
        }
    }
}

fun ModelQuery.execute(rootNode: INode): Set<INode> {
    return BulkModelQueryExecutor(rootNode).execute(this)
}