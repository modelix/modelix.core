/*
 * Copyright (c) 2022.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.modelix.model.server.light

import org.modelix.incremental.IncrementalIndex
import org.modelix.incremental.IncrementalList
import org.modelix.model.api.INode
import org.modelix.model.api.INodeReference
import org.modelix.model.server.api.ModelQuery
import org.modelix.model.server.api.RootOrSubquery
import org.modelix.model.server.api.RootQuery
import org.modelix.model.server.api.Subquery

/**
 * Not thread safe.
 */
class IncrementalModelQueryExecutor(val rootNode: INode) {
    private var lastUpdateSession: UpdateSession? = null
    private var currentUpdateSession: UpdateSession? = null

    private val nodeEntriesIndex: IncrementalIndex<INodeReference, NodeCacheEntry> = IncrementalIndex()

    fun invalidate(changedNodes: Set<INodeReference>) {
        lastUpdateSession?.invalidate(changedNodes)
    }

    /**
     * Returns the nodes that changed since the last execution
     */
    fun update(query: ModelQuery, visitor: (INode) -> Unit) {
        if (currentUpdateSession != null) throw IllegalStateException("Already executing a query")
        val updateSession = UpdateSession(lastUpdateSession?.cacheEntry?.takeIf { it.modelQuery == query } ?: ModelQueryCacheEntry(query, rootNode))
        try {
            currentUpdateSession = updateSession

            currentUpdateSession!!.cacheEntry.validate(visitor)
            nodeEntriesIndex.update(currentUpdateSession!!.cacheEntry.listOfAllNodeEntries)

            lastUpdateSession = currentUpdateSession
        } finally {
            currentUpdateSession = null
        }
    }

    private inner class UpdateSession(val cacheEntry: ModelQueryCacheEntry) {
        fun invalidate(invalidatedNodes: Set<INodeReference>) {
            invalidatedNodes.asSequence().flatMap { nodeEntriesIndex.lookup(it) }.forEach { it.invalidate() }
        }
    }
}

private sealed class CacheEntry() {
    abstract val parent: CacheEntry?
    private var valid = false
    private var anyDescendantInvalid = true

    var listOfAllNodeEntries: IncrementalList<Pair<INodeReference, NodeCacheEntry>> = IncrementalList.empty()

    protected abstract fun doValidate(validationVisitor: IValidationVisitor)
    protected abstract fun getChildren(): Sequence<CacheEntry>

    fun isValid() = valid
    fun isAnyTransitiveInvalid() = anyDescendantInvalid

    fun validate(validationVisitor: IValidationVisitor) {
        if (valid) {
            if (anyDescendantInvalid) {
                validateDescendants(validationVisitor)
            }
        } else {
            doValidate(validationVisitor)
            validateDescendants(validationVisitor)
            valid = true
        }
    }

    fun validateDescendants(validationVisitor: IValidationVisitor) {
        getChildren().forEach { it.validate(validationVisitor) }
        updateListOfAllNodeEntries()
        anyDescendantInvalid = false
    }

    fun invalidate() {
        valid = false
        parent?.descendantInvalidated()
    }

    fun descendantInvalidated() {
        if (anyDescendantInvalid) return
        anyDescendantInvalid = true
        parent?.descendantInvalidated()
    }

    protected open fun updateListOfAllNodeEntries() {
        listOfAllNodeEntries = IncrementalList.concat(getChildren().map { it.listOfAllNodeEntries }.toList())
    }
}

private class NodeCacheEntry(override val parent: CacheEntry, val node: INode, val producedByQuery: RootOrSubquery) : CacheEntry() {
    var filterResult = true
    private var children: List<SubqueryCacheEntry> = emptyList()

    override fun doValidate(validationVisitor: IValidationVisitor) {
        filterResult = producedByQuery.applyFilters(node)
        if (filterResult) validationVisitor(node)
        children = if (filterResult) {
            producedByQuery.queries.map { SubqueryCacheEntry(this, it) }
        } else {
            emptyList()
        }
    }

    override fun getChildren(): Sequence<CacheEntry> {
        return children.asSequence()
    }

    protected override fun updateListOfAllNodeEntries() {
        listOfAllNodeEntries = IncrementalList.concat((sequenceOf(IncrementalList.of(node.reference to this)) + getChildren().map { it.listOfAllNodeEntries }).toList())
    }
}

private sealed class QueryCacheEntry(parent: CacheEntry) : CacheEntry() {
    abstract val query: RootOrSubquery
    protected var children: Map<INode, NodeCacheEntry> = emptyMap()

    protected abstract fun queryNodes(): Sequence<INode>

    override fun doValidate(validationVisitor: IValidationVisitor) {
        children = queryNodes().associateWith { children[it] ?: NodeCacheEntry(this, it, query) }
    }

    override fun getChildren(): Sequence<CacheEntry> {
        return children.values.asSequence()
    }
}

private class SubqueryCacheEntry(override val parent: NodeCacheEntry, override val query: Subquery) : QueryCacheEntry(parent) {
    override fun queryNodes(): Sequence<INode> {
        return query.queryNodes(parent.node)
    }
}

private class RootQueryCacheEntry(override val parent: ModelQueryCacheEntry, override val query: RootQuery) : QueryCacheEntry(parent) {
    override fun queryNodes(): Sequence<INode> {
        return query.queryNodes(parent.rootNode)
    }
}

private class ModelQueryCacheEntry(val modelQuery: ModelQuery, val rootNode: INode) : CacheEntry() {
    private var children: List<RootQueryCacheEntry> = modelQuery.queries.map { RootQueryCacheEntry(this, it) }

    override val parent: CacheEntry?
        get() = null

    override fun doValidate(validationVisitor: IValidationVisitor) {
    }

    override fun getChildren(): Sequence<CacheEntry> {
        return children.asSequence()
    }
}

private typealias IValidationVisitor = (INode) -> Unit
