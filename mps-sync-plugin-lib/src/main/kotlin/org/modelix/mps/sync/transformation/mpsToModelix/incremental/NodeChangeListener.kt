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
import org.jetbrains.mps.openapi.language.SConcept
import org.jetbrains.mps.openapi.model.SNode
import org.jetbrains.mps.openapi.model.SNodeChangeListener
import org.modelix.kotlin.utils.UnstableModelixFeature
import org.modelix.model.api.IBranch
import org.modelix.model.api.INode
import org.modelix.model.api.PropertyFromName
import org.modelix.model.api.getNode
import org.modelix.model.data.NodeData
import org.modelix.model.mpsadapters.MPSChildLink
import org.modelix.model.mpsadapters.MPSConcept
import org.modelix.model.mpsadapters.MPSProperty
import org.modelix.model.mpsadapters.MPSReferenceLink
import org.modelix.mps.sync.transformation.MpsToModelixMap
import org.modelix.mps.sync.util.nodeIdAsLong
import org.modelix.mps.sync.util.runIfAlone
import java.util.concurrent.atomic.AtomicReference

@UnstableModelixFeature(reason = "The new modelix MPS plugin is under construction", intendedFinalization = "2024.1")
class NodeChangeListener(
    private val branch: IBranch,
    private val nodeMap: MpsToModelixMap,
    private val isSynchronizing: AtomicReference<Boolean>,
) : SNodeChangeListener {

    override fun propertyChanged(event: SPropertyChangeEvent) {
        isSynchronizing.runIfAlone {
            val property = MPSProperty(event.property)
            val nodeId = nodeMap[event.node]!!

            branch.runWriteT {
                val cloudNode = branch.getNode(nodeId)
                cloudNode.setPropertyValue(property, event.newValue)
            }
        }
    }

    override fun referenceChanged(event: SReferenceChangeEvent) {
        isSynchronizing.runIfAlone {
            // TODO fix me: it does not work correctly, if event.newValue.targetNode points to a node that is in a different model, that has not been synced yet to model server...

            val sourceNodeId = nodeMap[event.node]!!
            val reference = MPSReferenceLink(event.associationLink)
            val targetNodeId = event.newValue?.targetNode?.let { nodeMap[it] }

            branch.runWriteT {
                val cloudNode = branch.getNode(sourceNodeId)
                val target = if (targetNodeId == null) null else branch.getNode(targetNodeId)
                cloudNode.setReferenceTarget(reference, target)
            }
        }
    }

    override fun nodeAdded(event: SNodeAddEvent) {
        isSynchronizing.runIfAlone {
            val parentNodeId = if (event.isRoot) {
                nodeMap[event.model]!!
            } else {
                nodeMap[event.parent!!]!!
            }

            val containmentLink =
                event.aggregationLink ?: return@runIfAlone // ModelChangeListener.rootAdded handles it if null
            val childLink = MPSChildLink(containmentLink)

            val mpsChild = event.child
            val mpsConcept = mpsChild.concept

            val nodeId = nodeMap[mpsChild]
            val childExists = nodeId != null
            branch.runWriteT { transaction ->
                if (childExists) {
                    transaction.moveChild(parentNodeId, childLink.getSimpleName(), -1, nodeId!!)
                } else {
                    val cloudParentNode = branch.getNode(parentNodeId)
                    val cloudChildNode = cloudParentNode.addNewChild(childLink, -1, MPSConcept(mpsConcept))

                    // save the modelix ID and the SNode in the map
                    nodeMap.put(mpsChild, cloudChildNode.nodeIdAsLong())

                    synchronizeNodeToCloud(mpsConcept, mpsChild, cloudChildNode)
                }
            }
        }
    }

    private fun synchronizeNodeToCloud(
        mpsConcept: SConcept,
        mpsNode: SNode,
        cloudNode: INode,
    ) {
        // synchronize properties
        mpsConcept.properties.forEach {
            val mpsValue = mpsNode.getProperty(it)
            val modelixProperty = PropertyFromName(it.name)
            cloudNode.setPropertyValue(modelixProperty, mpsValue)
        }
        // save MPS Node ID explicitly
        val mpsNodeIdProperty = PropertyFromName(NodeData.ID_PROPERTY_KEY)
        cloudNode.setPropertyValue(mpsNodeIdProperty, mpsNode.nodeId.toString())

        // synchronize references
        mpsConcept.referenceLinks.forEach {
            val mpsTargetNode = mpsNode.getReferenceTarget(it)!!
            val targetNodeId = nodeMap[mpsTargetNode]!!

            val modelixReferenceLink = MPSReferenceLink(it)
            val cloudTargetNode = branch.getNode(targetNodeId)

            cloudNode.setReferenceTarget(modelixReferenceLink, cloudTargetNode)
        }

        // synchronize children
        mpsConcept.containmentLinks.forEach { containmentLink ->
            mpsNode.getChildren(containmentLink).forEach { mpsChild ->
                val childLink = MPSChildLink(containmentLink)
                val mpsChildConcept = mpsChild.concept
                val cloudChildNode = cloudNode.addNewChild(childLink, -1, MPSConcept(mpsChildConcept))

                // save the modelix ID and the SNode in the map
                nodeMap.put(mpsChild, cloudChildNode.nodeIdAsLong())

                synchronizeNodeToCloud(mpsChildConcept, mpsChild, cloudChildNode)
            }
        }
    }

    override fun nodeRemoved(event: SNodeRemoveEvent) {
        isSynchronizing.runIfAlone {
            val parentNodeId = if (event.isRoot) {
                nodeMap[event.model]!!
            } else {
                nodeMap[event.parent!!]!!
            }
            val nodeId = nodeMap[event.child]!!

            branch.runWriteT {
                val cloudParentNode = branch.getNode(parentNodeId)
                val cloudChildNode = branch.getNode(nodeId)
                cloudParentNode.removeChild(cloudChildNode)
            }
        }
    }
}
