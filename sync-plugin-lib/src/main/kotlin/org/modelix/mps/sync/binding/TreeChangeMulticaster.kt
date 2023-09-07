/*
 * Copyright (c) 2023.
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

package org.modelix.mps.sync.binding

import org.modelix.model.api.ITreeChangeVisitor
import org.modelix.model.api.ITreeChangeVisitorEx

// status: ready to test
/**
 * ITree.visitChanges can be expensive. The performance is better if all listeners iterate over the changes together.
 */
class TreeChangeMulticaster(private val visitors: List<ITreeChangeVisitor>) : ITreeChangeVisitorEx {

    override fun childrenChanged(nodeId: Long, role: String?) = visitors.forEach { it.childrenChanged(nodeId, role) }

    override fun containmentChanged(nodeId: Long) = visitors.forEach { it.containmentChanged(nodeId) }

    override fun propertyChanged(nodeId: Long, role: String) = visitors.forEach { it.propertyChanged(nodeId, role) }

    override fun referenceChanged(nodeId: Long, role: String) = visitors.forEach { it.referenceChanged(nodeId, role) }

    override fun nodeAdded(nodeId: Long) =
        visitors.filterIsInstance<ITreeChangeVisitorEx>().forEach { it.nodeAdded(nodeId) }

    override fun nodeRemoved(nodeId: Long) =
        visitors.filterIsInstance<ITreeChangeVisitorEx>().forEach { it.nodeRemoved(nodeId) }
}
