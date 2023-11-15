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

package org.modelix.mps.sync.neu.listeners

import org.jetbrains.mps.openapi.event.SNodeAddEvent
import org.jetbrains.mps.openapi.event.SNodeRemoveEvent
import org.jetbrains.mps.openapi.event.SPropertyChangeEvent
import org.jetbrains.mps.openapi.event.SReferenceChangeEvent
import org.jetbrains.mps.openapi.language.SConcept
import org.jetbrains.mps.openapi.model.SModel
import org.jetbrains.mps.openapi.model.SNode
import org.jetbrains.mps.openapi.model.SNodeChangeListener
import org.modelix.model.api.INode
import org.modelix.model.api.PropertyFromName
import org.modelix.model.api.getNode
import org.modelix.model.client2.ReplicatedModel
import org.modelix.model.mpsadapters.MPSChildLink
import org.modelix.model.mpsadapters.MPSConcept
import org.modelix.model.mpsadapters.MPSModelAsNode
import org.modelix.model.mpsadapters.MPSNode
import org.modelix.model.mpsadapters.MPSProperty
import org.modelix.model.mpsadapters.MPSReferenceLink
import org.modelix.mps.sync.util.nodeIdAsLong

// TODO test my methods in debug mode!!!
class NodeChangeListener(val mpsModel: SModel, modelixModel: ReplicatedModel) : SNodeChangeListener {

    private val branch = modelixModel.getBranch()

    override fun propertyChanged(event: SPropertyChangeEvent) {
        val property = MPSProperty(event.property)
        val nodeId = MPSNode(event.node).nodeIdAsLong()

        branch.runWriteT {
            val cloudNode = branch.getNode(nodeId)
            cloudNode.setPropertyValue(property, event.newValue)
        }
    }

    override fun referenceChanged(event: SReferenceChangeEvent) {
        val sourceNodeId = MPSNode(event.node).nodeIdAsLong()
        val reference = MPSReferenceLink(event.associationLink)
        val targetNodeId = event.newValue?.targetNode?.let { MPSNode(it).nodeIdAsLong() }

        branch.runWriteT {
            val cloudNode = branch.getNode(sourceNodeId)

            if (targetNodeId == null) {
                val target: INode? = null
                cloudNode.setReferenceTarget(reference, target)
            } else {
                val targetNode = branch.getNode(targetNodeId)
                cloudNode.setReferenceTarget(reference, targetNode)
            }
        }
    }

    override fun nodeAdded(event: SNodeAddEvent) {
        // TODO #1
        // What is the order of events, when we add a subtree in the model? Do we get nodeAdded events for every level
        // in the subtree or just for the top level?

        val parentNodeId = (if (event.isRoot) MPSModelAsNode(mpsModel) else MPSNode(event.parent!!)).nodeIdAsLong()
        val childLink = MPSChildLink(event.aggregationLink!!)

        val mpsChild = event.child
        val mpsConcept = mpsChild.concept

        val child = MPSNode(mpsChild)
        val nodeId = child.nodeIdAsLong()
        val childConcept = child.concept

        // if child does not exist in the repo yet, then we have to create it!
        branch.runWriteT { transaction ->
            val cloudParentNode = branch.getNode(parentNodeId)
            var cloudChildNode = branch.getNode(nodeId)

            val childExists = cloudChildNode.isValid
            if (childExists) {
                transaction.moveChild(parentNodeId, childLink.getSimpleName(), -1, nodeId)
            } else {
                // TODO #2 maintain a Map between the MPS NodeId and the cloud NodeId, because
                //  the generated cloud NodeId is not necessarily the same as the NodeId in MPS
                cloudChildNode = cloudParentNode.addNewChild(childLink, -1, childConcept)
                synchronizeNodeToCloud(mpsConcept, mpsChild, cloudChildNode)
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

        // synchronize references
        mpsConcept.referenceLinks.forEach {
            val mpsTargetNode = mpsNode.getReferenceTarget(it)!!
            val targetNodeId = MPSNode(mpsTargetNode).nodeIdAsLong()

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
                synchronizeNodeToCloud(mpsChildConcept, mpsChild, cloudChildNode)
            }
        }
    }

    override fun nodeRemoved(event: SNodeRemoveEvent) {
        // TODO #1
        // What is the order of events, when we remove a subtree in the model?

        val parentNodeId = (if (event.isRoot) MPSModelAsNode(mpsModel) else MPSNode(event.parent!!)).nodeIdAsLong()
        val nodeId = MPSNode(event.child).nodeIdAsLong()

        branch.runWriteT {
            val cloudParentNode = branch.getNode(parentNodeId)
            val cloudChildNode = branch.getNode(nodeId)

            // TODO #2 maybe we have to detach the node instead of deleting it...
            cloudParentNode.removeChild(cloudChildNode)
            // alternative:
            // it.moveChild(ITree.ROOT_ID, ITree.DETACHED_NODES_ROLE, -1, nodeId)
        }
    }
}
