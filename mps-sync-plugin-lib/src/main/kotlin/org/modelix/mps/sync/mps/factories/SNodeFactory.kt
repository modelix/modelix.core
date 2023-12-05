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

package org.modelix.mps.sync.mps.factories

import jetbrains.mps.smodel.adapter.MetaAdapterByDeclaration
import jetbrains.mps.smodel.adapter.structure.ref.SReferenceLinkAdapter2
import org.jetbrains.mps.openapi.language.SAbstractConcept
import org.jetbrains.mps.openapi.language.SConcept
import org.jetbrains.mps.openapi.language.SInterfaceConcept
import org.jetbrains.mps.openapi.language.SReferenceLink
import org.jetbrains.mps.openapi.model.SModel
import org.jetbrains.mps.openapi.model.SNode
import org.jetbrains.mps.openapi.model.SNodeId
import org.jetbrains.mps.openapi.module.ModelAccess
import org.jetbrains.mps.openapi.persistence.PersistenceFacade
import org.modelix.kotlin.utils.UnstableModelixFeature
import org.modelix.model.api.BuiltinLanguages
import org.modelix.model.api.INode
import org.modelix.model.api.PropertyFromName
import org.modelix.model.mpsadapters.MPSLanguageRepository
import org.modelix.model.mpsadapters.MPSReferenceLink
import org.modelix.mps.sync.mps.util.runWriteActionInEDTBlocking
import org.modelix.mps.sync.transformation.MpsToModelixMap
import org.modelix.mps.sync.util.mappedMpsNodeID
import org.modelix.mps.sync.util.nodeIdAsLong

@UnstableModelixFeature(reason = "The new modelix MPS plugin is under construction", intendedFinalization = "2024.1")
class SNodeFactory(
    private val conceptRepository: MPSLanguageRepository,
    private val modelAccess: ModelAccess,
    private val nodeMap: MpsToModelixMap,
) {

    private val resolvableReferences = mutableListOf<ResolvableReference>()

    fun createNode(iNode: INode, model: SModel?): SNode {
        val conceptId = iNode.concept?.getUID()!!
        val concept: SConcept = when (val rawConcept = conceptRepository.getConcept(conceptId)) {
            is SInterfaceConcept -> {
                MetaAdapterByDeclaration.asInstanceConcept((rawConcept as SAbstractConcept))
            }

            is SConcept -> {
                rawConcept
            }

            else -> throw IllegalStateException("Unknown raw concept: $rawConcept")
        }

        // 1. create node
        val mpsNodeId = getMpsNodeId(iNode)
        val sNode = jetbrains.mps.smodel.SNode(concept, mpsNodeId)
        val nodeId = iNode.nodeIdAsLong()

        // 2. add to parent
        val parent = iNode.parent
        val parentSerializedModelId =
            parent?.getPropertyValue(BuiltinLanguages.MPSRepositoryConcepts.Model.id) ?: ""
        val parentModelId = if (parentSerializedModelId.isNotEmpty()) {
            PersistenceFacade.getInstance().createModelId(parentSerializedModelId)
        } else {
            null
        }
        val modelIsTheParent = parentModelId != null && model?.modelId == parentModelId
        val isRootNode = concept.isRootable && modelIsTheParent

        if (isRootNode) {
            modelAccess.runWriteActionInEDTBlocking {
                model?.addRootNode(sNode)
            }
        } else {
            val parentNodeId = parent?.nodeIdAsLong()
            val parentNode = nodeMap.getNode(parentNodeId)
            check(parentNode != null) { "Parent of Node($nodeId) is not found. Node will not be added to the model." }

            val role = iNode.getContainmentLink()
            val containmentLink = parentNode.concept.containmentLinks.first { it.name == role?.getSimpleName() }
            modelAccess.runWriteActionInEDTBlocking {
                parentNode.addChild(containmentLink, sNode)
            }
        }
        nodeMap.put(sNode, nodeId)

        // 3. set properties
        setProperties(iNode, sNode)

        // 4. set references
        prepareLinkReferences(iNode)

        return sNode
    }

    private fun getMpsNodeId(iNode: INode): SNodeId {
        val mpsNodeIdAsString = iNode.mappedMpsNodeID()
        val mpsId = mpsNodeIdAsString?.let { PersistenceFacade.getInstance().createNodeId(it) }
        return if (mpsId != null) {
            mpsId
        } else {
            val id = iNode.nodeIdAsLong()
            jetbrains.mps.smodel.SNodeId.Regular(id)
        }
    }

    private fun setProperties(source: INode, target: SNode) {
        target.concept.properties.forEach {
            val property = PropertyFromName(it.name)
            val value = source.getPropertyValue(property)

            modelAccess.runWriteActionInEDTBlocking {
                target.setProperty(it, value)
            }
        }
    }

    private fun prepareLinkReferences(iNode: INode) {
        iNode.getAllReferenceTargets().forEach {
            val sourceNodeId = iNode.nodeIdAsLong()
            val source = nodeMap.getNode(sourceNodeId)!!

            val sReferenceLink = (it.first as MPSReferenceLink).link
            val reference = SReferenceLinkAdapter2(sReferenceLink.id, sReferenceLink.name)

            // TODO what about those references whose target is outside of the model?
            val targetNodeId = it.second.nodeIdAsLong()

            resolvableReferences.add(ResolvableReference(source, reference, targetNodeId))
        }
    }

    fun resolveReferences(mpsWriteAction: ((Runnable) -> Unit) = modelAccess::runWriteActionInEDTBlocking) {
        resolvableReferences.forEach {
            val source = it.source
            val reference = it.reference
            val target = nodeMap.getNode(it.targetNodeId)
            mpsWriteAction {
                source.setReferenceTarget(reference, target)
            }
        }
    }

    fun clearResolvableReferences() = resolvableReferences.clear()
}

@UnstableModelixFeature(reason = "The new modelix MPS plugin is under construction", intendedFinalization = "2024.1")
data class ResolvableReference(
    val source: SNode,
    val reference: SReferenceLink,
    val targetNodeId: Long,
)
