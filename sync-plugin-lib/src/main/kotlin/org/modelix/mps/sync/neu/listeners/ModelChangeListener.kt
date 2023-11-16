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

import jetbrains.mps.smodel.SModelInternal
import jetbrains.mps.smodel.event.SModelChildEvent
import jetbrains.mps.smodel.event.SModelDevKitEvent
import jetbrains.mps.smodel.event.SModelImportEvent
import jetbrains.mps.smodel.event.SModelLanguageEvent
import jetbrains.mps.smodel.event.SModelListener
import jetbrains.mps.smodel.event.SModelPropertyEvent
import jetbrains.mps.smodel.event.SModelReferenceEvent
import jetbrains.mps.smodel.event.SModelRenamedEvent
import jetbrains.mps.smodel.event.SModelRootEvent
import jetbrains.mps.smodel.loading.ModelLoadingState
import org.jetbrains.mps.openapi.language.SConcept
import org.jetbrains.mps.openapi.model.SModel
import org.jetbrains.mps.openapi.model.SNode
import org.modelix.model.api.BuiltinLanguages
import org.modelix.model.api.INode
import org.modelix.model.api.PropertyFromName
import org.modelix.model.api.getNode
import org.modelix.model.client2.ReplicatedModel
import org.modelix.model.mpsadapters.MPSChildLink
import org.modelix.model.mpsadapters.MPSConcept
import org.modelix.model.mpsadapters.MPSProperty
import org.modelix.model.mpsadapters.MPSReferenceLink
import org.modelix.model.mpsadapters.Model
import org.modelix.mps.sync.neu.MpsToModelixMap
import org.modelix.mps.sync.util.nodeIdAsLong

// TODO test all methods in debugger
class ModelChangeListener(
    modelixModel: ReplicatedModel,
    private val nodeMap: MpsToModelixMap,
    private val nodeChangeListener: NodeChangeListener,
) : SModelListener {

    private val branch = modelixModel.getBranch()

    override fun importAdded(event: SModelImportEvent) {
        val modelixId = nodeMap[event.model]!!
        val mpsModelReference = event.modelUID
        val targetModel = mpsModelReference.resolve(event.model.repository)

        val modelImportsLink = BuiltinLanguages.MPSRepositoryConcepts.Model.modelImports
        val modelReferenceConcept = BuiltinLanguages.MPSRepositoryConcepts.ModelReference

        branch.runWriteT {
            val cloudParentNode = branch.getNode(modelixId)
            val cloudModelReference = cloudParentNode.addNewChild(modelImportsLink, -1, modelReferenceConcept)

            // save the modelix ID and the SNode in the map
            nodeMap.put(mpsModelReference, cloudModelReference.nodeIdAsLong())

            // warning: might be fragile, because we just sync the "model" reference, but no other fields
            val targetModelModelixId = nodeMap[targetModel]!!
            val cloudTargetModel = branch.getNode(targetModelModelixId)
            cloudModelReference.setReferenceTarget(
                BuiltinLanguages.MPSRepositoryConcepts.ModelReference.model,
                cloudTargetModel,
            )
        }
    }

    override fun importRemoved(event: SModelImportEvent) {
        // TODO deduplicate implementation
        val parentNodeId = nodeMap[event.model]!!
        val nodeId = nodeMap[event.modelUID]!!
        branch.runWriteT {
            val cloudParentNode = branch.getNode(parentNodeId)
            val cloudChildNode = branch.getNode(nodeId)
            cloudParentNode.removeChild(cloudChildNode)
        }
    }

    override fun languageAdded(event: SModelLanguageEvent) {
        TODO("Not yet implemented")
    }

    override fun languageRemoved(event: SModelLanguageEvent) {
        // TODO
        // model.usedLanguages között megkeresni az ehhez tartozó referenciát és annak a target nodeját törölni
    }

    override fun devkitAdded(event: SModelDevKitEvent) {
        // TODO
        // ez egy DevKit dependency lesz, aminek a mezőit meg kell példányosítani a szerveren
        // a DevKit dependencyt pedig a modell "usedLanguages" containment referenciájában mentjük el
        val modelixId = nodeMap[event.model]!!

        // TODO de miért egy moduleIdra mutat?
        event.devkitNamespace.moduleId

        branch.runWriteT {
            val cloudNode = branch.getNode(modelixId)
        }
    }

    override fun devkitRemoved(event: SModelDevKitEvent) {
        // TODO
        // model.usedLanguages között megkeresni az ehhez tartozó referenciát és annak a target nodeját törölni
    }

    @Deprecated("Deprecated in Java")
    override fun rootAdded(event: SModelRootEvent) {
        // TODO deduplicate implementation
        val parentNodeId = nodeMap[event.model]!!
        val childLink = Model.rootNodes

        val mpsChild = event.root
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

    @Deprecated("Deprecated in Java")
    override fun rootRemoved(event: SModelRootEvent) {
        // TODO deduplicate implementation
        val parentNodeId = nodeMap[event.model]!!
        val nodeId = nodeMap[event.root]!!
        branch.runWriteT {
            val cloudParentNode = branch.getNode(parentNodeId)
            val cloudChildNode = branch.getNode(nodeId)
            cloudParentNode.removeChild(cloudChildNode)
        }
    }

    override fun modelRenamed(event: SModelRenamedEvent) {
        // TODO deduplicate implementation
        val modelixId = nodeMap[event.model]!!
        branch.runWriteT {
            val cloudNode = branch.getNode(modelixId)
            cloudNode.setPropertyValue(BuiltinLanguages.jetbrains_mps_lang_core.INamedConcept.name, event.newName)
        }
    }

    override fun propertyChanged(event: SModelPropertyEvent) {
        // TODO deduplicate implementation
        val modelixId = nodeMap[event.model]!!
        val property = MPSProperty(event.property)
        branch.runWriteT {
            val cloudNode = branch.getNode(modelixId)
            cloudNode.setPropertyValue(property, event.newPropertyValue)
        }
    }

    override fun childAdded(event: SModelChildEvent) {
        // TODO #1 when is this method called?
        // TODO #2 deduplicate implementation
        val parentNodeId = nodeMap[event.parent]!!
        val childLink = Model.rootNodes

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

    override fun childRemoved(event: SModelChildEvent) {
        // TODO #1 when is this method called?
        // TODO #2 deduplicate implementation
        val parentNodeId = nodeMap[event.model]!!
        val nodeId = nodeMap[event.child]!!
        branch.runWriteT {
            val cloudParentNode = branch.getNode(parentNodeId)
            val cloudChildNode = branch.getNode(nodeId)
            cloudParentNode.removeChild(cloudChildNode)
        }
    }

    override fun beforeModelDisposed(model: SModel) {
        model.removeChangeListener(nodeChangeListener)
        (model as? SModelInternal)?.removeModelListener(this)
    }

    override fun getPriority(): SModelListener.SModelListenerPriority = SModelListener.SModelListenerPriority.CLIENT

    /* incoming reference, not interesting for us */
    override fun referenceAdded(event: SModelReferenceEvent) {}
    override fun referenceRemoved(event: SModelReferenceEvent) {}
    override fun beforeChildRemoved(event: SModelChildEvent) {}
    override fun beforeRootRemoved(event: SModelRootEvent) {}
    override fun beforeModelRenamed(event: SModelRenamedEvent) {}
    override fun modelSaved(model: SModel) {}
    override fun modelLoadingStateChanged(model: SModel?, state: ModelLoadingState) {}

    // TODO deduplicate implementation
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
}
