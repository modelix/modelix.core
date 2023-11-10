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

package org.modelix.mps.sync.neu

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
import org.modelix.model.api.INode
import org.modelix.model.api.PropertyFromName
import org.modelix.model.mpsadapters.MPSLanguageRepository
import org.modelix.model.mpsadapters.MPSReferenceLink
import org.modelix.mps.sync.util.mappedMpsNodeID
import org.modelix.mps.sync.util.nodeIdAsLong

class SNodeFactory(val conceptRepository: MPSLanguageRepository, val modelAccess: ModelAccess) {

    private val nodeMapping = mutableMapOf<SNodeId, SNode>()
    private val resolveableReferences = mutableListOf<ResolveableReference>()

    fun createNode(iNode: INode, model: SModel?): SNode {
        val nodeId = getNodeId(iNode)

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
        val sNode = jetbrains.mps.smodel.SNode(concept, nodeId)
        nodeMapping[nodeId] = sNode

        // 2. add to parent
        // TODO check if parent is the model ID, otherwise lookup the parent node
        modelAccess.runWriteAction {
            if (concept.isRootable) {
                model?.addRootNode(sNode)
            } else {
                val parent = iNode.parent?.let { getNodeId(it) }
                val parentNode = nodeMapping[parent]
                parentNode?.let {
                    val role = iNode.getContainmentLink()
                    val containmentLink = parentNode.concept.containmentLinks.first { it.name == role?.getSimpleName() }
                    modelAccess.executeCommandInEDT {
                        parentNode.addChild(containmentLink, sNode)
                    }
                }
            }
        }

        // 3. set properties
        setProperties(iNode, sNode)

        // 4. set references
        prepareLinkReferences(iNode, concept)

        return sNode
    }

    private fun getNodeId(iNode: INode): SNodeId {
        val mpsNodeIdAsString = iNode.mappedMpsNodeID()
        return if (mpsNodeIdAsString == null) {
            val id = iNode.nodeIdAsLong()
            jetbrains.mps.smodel.SNodeId.Regular(id)
        } else {
            PersistenceFacade.getInstance().createNodeId(mpsNodeIdAsString)!!
        }
    }

    private fun setProperties(source: INode, target: SNode) {
        target.concept.properties.forEach {
            val property = PropertyFromName(it.name)
            val value = source.getPropertyValue(property)

            modelAccess.runWriteAction {
                modelAccess.executeCommandInEDT {
                    target.setProperty(it, value)
                }
            }
        }
    }

    private fun prepareLinkReferences(iNode: INode, concept: SConcept) {
        iNode.getAllReferenceTargets().forEach {
            val sourceNodeId = getNodeId(iNode)
            val source = nodeMapping[sourceNodeId]!!

            val sReferenceLink = (it.first as MPSReferenceLink).link
            val reference = SReferenceLinkAdapter2(sReferenceLink.id, sReferenceLink.name)

            // TODO what about those references whose target is outside of the model?
            val targetNodeId = getNodeId(it.second)

            resolveableReferences.add(ResolveableReference(source, reference, targetNodeId))
        }
    }

    fun resolveReferences() {
        resolveableReferences.forEach {
            val source = it.source
            val reference = it.reference
            val target = nodeMapping[it.targetNodeId]

            modelAccess.runWriteAction {
                modelAccess.executeCommandInEDT {
                    source.setReferenceTarget(reference, target)
                }
            }
        }
    }
}

data class ResolveableReference(
    val source: SNode,
    val reference: SReferenceLink,
    val targetNodeId: SNodeId,
)
