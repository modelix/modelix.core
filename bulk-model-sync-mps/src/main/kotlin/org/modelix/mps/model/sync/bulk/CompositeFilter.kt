/*
 * Copyright (c) 2024.
 *
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

package org.modelix.mps.model.sync.bulk

import org.modelix.model.api.INode
import org.modelix.model.sync.bulk.ModelSynchronizer

/**
 * Filter representing the intersection of multiple filters.
 * The filter will evaluate to true iff all [filters] evaluate to true.
 *
 * @param filters collection of filters. If the collection is ordered, the filters will be evaluated in the specified order.
 */
class CompositeFilter(private val filters: Collection<ModelSynchronizer.IFilter>) : ModelSynchronizer.IFilter {

    override fun needsDescentIntoSubtree(subtreeRoot: INode): Boolean = filters.all { it.needsDescentIntoSubtree(subtreeRoot) }

    override fun needsSynchronization(node: INode): Boolean = filters.all { it.needsSynchronization(node) }
}
