package org.modelix.model.lazy

import org.modelix.model.api.ITree

@Deprecated("use ModelQL")
interface IBulkTree : ITree {

    @Deprecated("use ModelQL")
    fun getDescendants(root: Long, includeSelf: Boolean): Iterable<CLNode>

    @Deprecated("use ModelQL")
    fun getDescendants(roots: Iterable<Long>, includeSelf: Boolean): Iterable<CLNode>

    @Deprecated("use ModelQL")
    fun getAncestors(nodeIds: Iterable<Long>, includeSelf: Boolean): Set<Long>
}
