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

package org.modelix.model.sync.bulk

import org.modelix.model.api.INode
import org.modelix.model.api.INodeReference

/**
 * Is responsible for storing the mapping between a source node and the imported target node.
 * Provides efficient lookup of the mapping from previous synchronization runs.
 */
interface INodeAssociation {
    fun resolveTarget(sourceNode: INode): INode?
    fun associate(sourceNode: INode, targetNode: INode)

    /**
     * A possibly faster implementation that avoids calls to resolveTarget
     */
    fun associationMatches(sourceNode: INode, targetNode: INode): Boolean
}