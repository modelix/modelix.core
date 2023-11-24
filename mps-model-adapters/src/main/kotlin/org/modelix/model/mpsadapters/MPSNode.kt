/*
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
package org.modelix.model.mpsadapters

import jetbrains.mps.lang.smodel.generator.smodelAdapter.SNodeOperations
import jetbrains.mps.smodel.MPSModuleRepository
import jetbrains.mps.smodel.adapter.MetaAdapterByDeclaration
import org.jetbrains.mps.openapi.model.SNode
import org.modelix.model.api.BuiltinLanguages
import org.modelix.model.api.ConceptReference
import org.modelix.model.api.IChildLink
import org.modelix.model.api.IConcept
import org.modelix.model.api.IConceptReference
import org.modelix.model.api.IDeprecatedNodeDefaults
import org.modelix.model.api.INode
import org.modelix.model.api.INodeReference
import org.modelix.model.api.IProperty
import org.modelix.model.api.IReferenceLink
import org.modelix.model.api.resolveIn
import org.modelix.model.area.IArea

data class MPSNode(val node: SNode) : IDeprecatedNodeDefaults {
    override fun getArea(): IArea {
        return MPSArea(node.model?.repository ?: MPSModuleRepository.getInstance())
    }

    override val isValid: Boolean
        get() = true
    override val reference: INodeReference
        get() = MPSNodeReference(node.reference)
    override val concept: IConcept
        get() = MPSConcept(node.concept)
    override val parent: INode?
        get() = node.parent?.let { MPSNode(it) } ?: node.model?.let { MPSModelAsNode(it) }

    override fun getConceptReference(): ConceptReference {
        return concept.getReference() as ConceptReference
    }

    override val allChildren: Iterable<INode>
        get() = node.children.map { MPSNode(it) }

    override fun removeChild(child: INode) {
        require(child is MPSNode) { "child must be an MPSNode" }
        node.removeChild(child.node)
    }

    override fun getPropertyLinks(): List<IProperty> {
        return node.properties.map { MPSProperty(it) }
    }

    override fun getReferenceLinks(): List<IReferenceLink> {
        return node.references.map { MPSReferenceLink(it.link) }
    }

    override fun getContainmentLink(): IChildLink {
        return node.containmentLink?.let { MPSChildLink(it) } ?: BuiltinLanguages.MPSRepositoryConcepts.Model.rootNodes
    }

    override fun getChildren(link: IChildLink): Iterable<INode> {
        return node.children.map { MPSNode(it) }.filter {
            val actualLink = it.getContainmentLink() ?: return@filter false
            actualLink.getUID() == link.getUID() ||
                actualLink.getSimpleName() == link.getSimpleName() ||
                link.getUID().contains(actualLink.getSimpleName())
        }
    }

    override fun moveChild(role: IChildLink, index: Int, child: INode) {
        require(role is MPSChildLink) { "role must be an MPSChildLink" }

        val link = role.link
        val children = node.getChildren(link).toList()
        require(index <= children.size) { "index out of bounds: $index > ${children.size}" }

        require(child is MPSNode)
        val sChild = child.node
        SNodeOperations.deleteNode(sChild)

        if (index == -1 || index == children.size) {
            node.addChild(link, sChild)
        } else {
            node.insertChildBefore(link, sChild, children[index])
        }
    }

    override fun addNewChild(role: IChildLink, index: Int, concept: IConcept?): INode {
        require(role is MPSChildLink) { "role must be an MPSChildLink" }

        val link = role.link
        val children = node.getChildren(link).toList()
        require(index <= children.size) { "index out of bounds: $index > ${children.size}" }

        val targetConcept = if (concept is MPSConcept) concept.concept else link.targetConcept
        val instantiatableConcept = MetaAdapterByDeclaration.asInstanceConcept(targetConcept)

        val model = node.model
        val newChild = if (model == null) {
            jetbrains.mps.smodel.SNode(instantiatableConcept)
        } else {
            model.createNode(instantiatableConcept)
        }

        if (index == -1 || index == children.size) {
            node.addChild(link, newChild)
        } else {
            node.insertChildBefore(link, newChild, children[index])
        }
        return MPSNode(newChild)
    }

    override fun addNewChild(role: IChildLink, index: Int, concept: IConceptReference?): INode {
        val repo = checkNotNull(node.model?.repository)
        val targetConcept = concept?.let { MPSLanguageRepository(repo).resolveConcept(it.getUID()) }
        return addNewChild(role, index, targetConcept)
    }

    override fun getReferenceTarget(link: IReferenceLink): INode? {
        return node.references.filter { MPSReferenceLink(it.link).getUID() == link.getUID() }
            .firstOrNull()?.targetNode?.let { MPSNode(it) }
    }

    override fun setReferenceTarget(link: IReferenceLink, target: INode?) {
        val ref = node.references.first { MPSReferenceLink(it.link).getUID() == link.getUID() }
        val targetNode = target?.let { getArea().resolveNode(it.reference) } as MPSNode
        node.setReferenceTarget(ref.link, targetNode.node)
    }

    override fun setReferenceTarget(role: IReferenceLink, target: INodeReference?) {
        setReferenceTarget(role, target?.resolveIn(getArea()))
    }

    override fun getReferenceTargetRef(role: IReferenceLink): INodeReference? {
        return node.references.firstOrNull { MPSReferenceLink(it.link).getUID() == role.getUID() }
            ?.targetNodeReference?.let { MPSNodeReference(it) }
    }

    override fun getPropertyValue(property: IProperty): String? {
        if (property.isIdProperty()) {
            return node.nodeId.toString()
        }
        val mpsProperty = node.properties.firstOrNull { MPSProperty(it).getUID() == property.getUID() } ?: return null
        return node.getProperty(mpsProperty)
    }

    override fun setPropertyValue(property: IProperty, value: String?) {
        val mpsProperty = node.properties.first { MPSProperty(it).getUID() == property.getUID() }
        node.setProperty(mpsProperty, value)
    }
}
