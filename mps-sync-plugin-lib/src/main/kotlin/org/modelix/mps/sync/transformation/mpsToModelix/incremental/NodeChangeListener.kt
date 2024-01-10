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

package org.modelix.mps.sync.transformation.mpsToModelix.incremental

import org.jetbrains.mps.openapi.event.SNodeAddEvent
import org.jetbrains.mps.openapi.event.SNodeRemoveEvent
import org.jetbrains.mps.openapi.event.SPropertyChangeEvent
import org.jetbrains.mps.openapi.event.SReferenceChangeEvent
import org.jetbrains.mps.openapi.model.SNodeChangeListener
import org.modelix.kotlin.utils.UnstableModelixFeature
import org.modelix.model.api.IBranch
import org.modelix.mps.sync.transformation.cache.MpsToModelixMap
import org.modelix.mps.sync.transformation.mpsToModelix.initial.NodeSynchronizer
import org.modelix.mps.sync.util.SyncBarrier

@UnstableModelixFeature(reason = "The new modelix MPS plugin is under construction", intendedFinalization = "2024.1")
class NodeChangeListener(
    branch: IBranch,
    private val nodeMap: MpsToModelixMap,
    isSynchronizing: SyncBarrier,
) : SNodeChangeListener {

    private val synchronizer = NodeSynchronizer(branch, nodeMap, isSynchronizing)

    override fun nodeAdded(event: SNodeAddEvent) = synchronizer.addNode(event.child)

    override fun nodeRemoved(event: SNodeRemoveEvent) = synchronizer.removeNode(
        parentNodeIdProducer = {
            if (event.isRoot) {
                nodeMap[event.model]!!
            } else {
                nodeMap[event.parent!!]!!
            }
        },
        childNodeIdProducer = { nodeMap[event.child]!! },
    )

    override fun propertyChanged(event: SPropertyChangeEvent) =
        synchronizer.setProperty(event.property, event.newValue) { nodeMap[event.node]!! }

    override fun referenceChanged(event: SReferenceChangeEvent) {
        // TODO fix me: it does not work correctly, if event.newValue.targetNode points to a node that is in a different model, that has not been synced yet to model server...
        synchronizer.setReference(
            event.associationLink,
            sourceNodeIdProducer = { nodeMap[event.node]!! },
            targetNodeIdProducer = { event.newValue?.targetNode?.let { nodeMap[it] } },
        )
    }
}
