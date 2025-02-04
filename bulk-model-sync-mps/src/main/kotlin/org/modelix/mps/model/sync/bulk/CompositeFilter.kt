package org.modelix.mps.model.sync.bulk

import org.modelix.model.api.IReadableNode
import org.modelix.model.sync.bulk.ModelSynchronizer

/**
 * Filter representing the intersection of multiple filters.
 * The filter will evaluate to true iff all [filters] evaluate to true.
 *
 * @param filters collection of filters. If the collection is ordered, the filters will be evaluated in the specified order.
 */
class CompositeFilter(private val filters: Collection<ModelSynchronizer.IFilter>) : ModelSynchronizer.IFilter {

    override fun needsDescentIntoSubtree(subtreeRoot: IReadableNode): Boolean = filters.all { it.needsDescentIntoSubtree(subtreeRoot) }

    override fun needsSynchronization(node: IReadableNode): Boolean = filters.all { it.needsSynchronization(node) }
}
