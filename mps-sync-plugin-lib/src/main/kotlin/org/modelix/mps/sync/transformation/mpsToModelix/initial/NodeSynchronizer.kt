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

package org.modelix.mps.sync.transformation.mpsToModelix.initial

import org.jetbrains.mps.openapi.language.SConcept
import org.jetbrains.mps.openapi.language.SProperty
import org.jetbrains.mps.openapi.language.SReferenceLink
import org.jetbrains.mps.openapi.model.SNode
import org.modelix.kotlin.utils.UnstableModelixFeature
import org.modelix.model.api.BuiltinLanguages
import org.modelix.model.api.IBranch
import org.modelix.model.api.IChildLink
import org.modelix.model.api.INode
import org.modelix.model.api.IProperty
import org.modelix.model.api.IReferenceLink
import org.modelix.model.api.PropertyFromName
import org.modelix.model.api.getNode
import org.modelix.model.data.NodeData
import org.modelix.model.mpsadapters.MPSChildLink
import org.modelix.model.mpsadapters.MPSConcept
import org.modelix.model.mpsadapters.MPSProperty
import org.modelix.model.mpsadapters.MPSReferenceLink
import org.modelix.mps.sync.transformation.cache.MpsToModelixMap
import org.modelix.mps.sync.util.SyncLock
import org.modelix.mps.sync.util.SyncQueue
import org.modelix.mps.sync.util.nodeIdAsLong

@UnstableModelixFeature(reason = "The new modelix MPS plugin is under construction", intendedFinalization = "2024.1")
class NodeSynchronizer(
    private val branch: IBranch,
    private val nodeMap: MpsToModelixMap,
    private val syncQueue: SyncQueue,
    private val resolvableReferences: MutableList<CloudResolvableReference>? = null,
) {

    fun addNode(node: SNode) {
        syncQueue.enqueue(linkedSetOf(SyncLock.MODELIX_WRITE, SyncLock.MPS_READ), true) {
            val parentNodeId = if (node.parent != null) {
                nodeMap[node.parent]!!
            } else {
                nodeMap[node.model]!!
            }

            val containmentLink = node.containmentLink
            val childLink: IChildLink = if (containmentLink == null) {
                if (node.parent == null) {
                    BuiltinLanguages.MPSRepositoryConcepts.Model.rootNodes
                } else {
                    throw IllegalStateException("Containment link of $node is null, thus node may not get synchronized to modelix")
                }
            } else {
                MPSChildLink(containmentLink)
            }

            val mpsConcept = node.concept
            val cloudParentNode = branch.getNode(parentNodeId)
            val cloudChildNode = cloudParentNode.addNewChild(childLink, -1, MPSConcept(mpsConcept))

            // save the modelix ID and the SNode in the map
            nodeMap.put(node, cloudChildNode.nodeIdAsLong())

            synchronizeNodeToCloud(mpsConcept, node, cloudChildNode)
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
            val modelixReferenceLink = MPSReferenceLink(it)
            val mpsTargetNode = mpsNode.getReferenceTarget(it)
            if (resolvableReferences != null) {
                resolvableReferences.add(CloudResolvableReference(cloudNode, modelixReferenceLink, mpsTargetNode))
            } else {
                setReferenceInTheCloud(cloudNode, modelixReferenceLink, mpsTargetNode)
            }
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

    private fun setReferenceInTheCloud(cloudNode: INode, modelixReferenceLink: IReferenceLink, mpsTargetNode: SNode?) {
        val cloudTargetNode = mpsTargetNode?.let {
            val targetNodeId = nodeMap[mpsTargetNode]!!
            branch.getNode(targetNodeId)
        }
        cloudNode.setReferenceTarget(modelixReferenceLink, cloudTargetNode)
    }

    fun setProperty(mpsProperty: SProperty, newValue: String, sourceNodeIdProducer: (MpsToModelixMap) -> Long) =
        setProperty(MPSProperty(mpsProperty), newValue, sourceNodeIdProducer)

    fun setProperty(property: IProperty, newValue: String, sourceNodeIdProducer: (MpsToModelixMap) -> Long) {
        syncQueue.enqueue(linkedSetOf(SyncLock.MODELIX_WRITE), true) {
            runSetPropertyAction(property, newValue, sourceNodeIdProducer)
        }
    }

    /**
     * WARNING: call this method only in a SyncTask, otherwise the necessary Modelix write transaction is missing
     */
    fun runSetPropertyAction(property: IProperty, newValue: String, sourceNodeIdProducer: (MpsToModelixMap) -> Long) {
        val nodeId = sourceNodeIdProducer.invoke(nodeMap)
        val cloudNode = branch.getNode(nodeId)
        cloudNode.setPropertyValue(property, newValue)
    }

    fun removeNode(parentNodeIdProducer: (MpsToModelixMap) -> Long, childNodeIdProducer: (MpsToModelixMap) -> Long) {
        syncQueue.enqueue(linkedSetOf(SyncLock.MODELIX_WRITE), true) {
            runRemoveNodeAction(parentNodeIdProducer, childNodeIdProducer)
        }
    }

    /**
     * WARNING: call this method only in a SyncTask, otherwise the necessary Modelix write transaction is missing
     */
    fun runRemoveNodeAction(
        parentNodeIdProducer: (MpsToModelixMap) -> Long,
        childNodeIdProducer: (MpsToModelixMap) -> Long,
    ) {
        val parentNodeId = parentNodeIdProducer.invoke(nodeMap)
        val nodeId = childNodeIdProducer.invoke(nodeMap)

        val cloudParentNode = branch.getNode(parentNodeId)
        val cloudChildNode = branch.getNode(nodeId)
        cloudParentNode.removeChild(cloudChildNode)

        nodeMap.remove(nodeId)
    }

    fun setReference(
        mpsReferenceLink: SReferenceLink,
        sourceNodeIdProducer: (MpsToModelixMap) -> Long,
        targetNodeIdProducer: (MpsToModelixMap) -> Long?,
    ) {
        syncQueue.enqueue(linkedSetOf(SyncLock.MODELIX_WRITE, SyncLock.MPS_READ), true) {
            val sourceNodeId = sourceNodeIdProducer.invoke(nodeMap)
            val targetNodeId = targetNodeIdProducer.invoke(nodeMap)
            val reference = MPSReferenceLink(mpsReferenceLink)

            val cloudNode = branch.getNode(sourceNodeId)
            val target = if (targetNodeId == null) null else branch.getNode(targetNodeId)
            cloudNode.setReferenceTarget(reference, target)
        }
    }

    fun resolveReferences() {
        syncQueue.enqueue(linkedSetOf(SyncLock.MODELIX_WRITE)) {
            resolvableReferences?.forEach { setReferenceInTheCloud(it.sourceNode, it.referenceLink, it.mpsTargetNode) }
        }
    }

    fun clearResolvableReferences() = resolvableReferences?.clear()
}

@UnstableModelixFeature(
    reason = "The new modelix MPS plugin is under construction",
    intendedFinalization = "2024.1",
)
data class CloudResolvableReference(
    val sourceNode: INode,
    val referenceLink: IReferenceLink,
    val mpsTargetNode: SNode?,
)
