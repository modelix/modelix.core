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
import org.jetbrains.mps.openapi.language.SAbstractConcept
import org.jetbrains.mps.openapi.language.SConcept
import org.jetbrains.mps.openapi.language.SInterfaceConcept
import org.jetbrains.mps.openapi.model.SModel
import org.jetbrains.mps.openapi.model.SNode
import org.jetbrains.mps.openapi.model.SNodeId
import org.jetbrains.mps.openapi.module.ModelAccess
import org.jetbrains.mps.openapi.persistence.PersistenceFacade
import org.modelix.model.api.INode
import org.modelix.model.api.IReadTransaction
import org.modelix.model.api.PropertyFromName
import org.modelix.model.mpsadapters.MPSLanguageRepository
import org.modelix.mps.sync.util.mappedMpsNodeID
import org.modelix.mps.sync.util.nodeIdAsLong

class SNodeFactory(
    val conceptRepository: MPSLanguageRepository,
    val modelAccess: ModelAccess,
    val transaction: IReadTransaction,
) {

    private val nodeMapping = mutableMapOf<SNodeId, SNode>()

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
        // TODO circular references and stuff, we might have to delay resolving the links...
        // setLinkReferences(iNode, sNode)

        // 5. transform children
        // TODO we have to transform the children first and then set them here --> check how it was done before
        // or we can do it like so: we traverse the model anyways, and when we get here we check if parent is an SNode. If it is, then we add this newly created node to the parent via the appropriate childLink.
        // setChildren(iNode, SNode)

        nodeMapping[nodeId] = sNode
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
}
