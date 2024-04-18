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
import jetbrains.mps.smodel.adapter.ids.SConceptId
import jetbrains.mps.smodel.adapter.ids.SContainmentLinkId
import jetbrains.mps.smodel.adapter.ids.SPropertyId
import jetbrains.mps.smodel.adapter.ids.SReferenceLinkId
import jetbrains.mps.smodel.adapter.structure.concept.SConceptAdapterById
import jetbrains.mps.smodel.adapter.structure.link.SContainmentLinkAdapterById
import jetbrains.mps.smodel.adapter.structure.property.SPropertyAdapterById
import jetbrains.mps.smodel.adapter.structure.ref.SReferenceLinkAdapterById
import org.jetbrains.mps.openapi.language.SContainmentLink
import org.jetbrains.mps.openapi.model.SNode
import org.modelix.incremental.DependencyTracking
import org.modelix.model.api.BuiltinLanguages
import org.modelix.model.api.ConceptReference
import org.modelix.model.api.IChildLink
import org.modelix.model.api.IConcept
import org.modelix.model.api.IConceptReference
import org.modelix.model.api.INode
import org.modelix.model.api.INodeReference
import org.modelix.model.api.IProperty
import org.modelix.model.api.IReferenceLink
import org.modelix.model.api.IReplaceableNode
import org.modelix.model.api.resolveIn
import org.modelix.model.area.IArea

data class MPSNode(val node: SNode) : IDefaultNodeAdapter, IReplaceableNode {
    override fun getArea(): IArea {
        return MPSArea(node.model?.repository ?: MPSModuleRepository.getInstance())
    }

    override val isValid: Boolean
        get() = true
    override val reference: INodeReference
        get() = MPSNodeReference(node.reference)
    override val concept: IConcept
        get() {
            DependencyTracking.accessed(MPSNodeDependency(node))
            return MPSConcept(node.concept)
        }
    override val parent: INode?
        get() {
            DependencyTracking.accessed(MPSContainmentDependency(node))
            return node.parent?.let { MPSNode(it) } ?: node.model?.let { MPSModelAsNode(it) }
        }

    override fun tryGetConcept(): IConcept {
        return MPSConcept(node.concept)
    }

    override fun getConceptReference(): ConceptReference {
        return concept.getReference() as ConceptReference
    }

    override val allChildren: Iterable<INode>
        get() {
            DependencyTracking.accessed(MPSAllChildrenDependency(node))
            return node.children.map { MPSNode(it) }
        }

    override fun replaceNode(concept: ConceptReference?): INode {
        requireNotNull(concept) { "Can't replace $node with null concept. Use BaseConcept explicitly." }

        val id = node.nodeId
        val model = checkNotNull(node.model) { "Node is not part of a model" }
        val newNode = model.createNode(SConceptAdapterById(SConceptId.deserialize(concept.uid), ""), id)
        node.properties.forEach { newNode.setProperty(it, node.getProperty(it)) }
        node.references.forEach { newNode.setReference(it.link, it.targetNodeReference) }
        node.children.forEach { child ->
            val link = checkNotNull(child.containmentLink) { "Containment link of child node not found" }
            newNode.addChild(link, child)
        }

        val parent = checkNotNull(node.parent) { "Cannot replace node without a parent" }
        parent.insertChildBefore(getMPSContainmentLink(getContainmentLink()), node, newNode)
        node.delete()
        return MPSNode(newNode)
    }

    override fun removeChild(child: INode) {
        require(child is MPSNode) { "child must be an MPSNode" }
        node.removeChild(child.node)
    }

    override fun getPropertyLinks(): List<IProperty> {
        DependencyTracking.accessed(MPSNodeDependency(node))
        return node.properties.map { MPSProperty(it) }
    }

    override fun getReferenceLinks(): List<IReferenceLink> {
        DependencyTracking.accessed(MPSNodeDependency(node))
        return node.references.map { MPSReferenceLink(it.link) }
    }

    override fun getContainmentLink(): IChildLink {
        DependencyTracking.accessed(MPSNodeDependency(node))
        return node.containmentLink?.let { MPSChildLink(it) } ?: BuiltinLanguages.MPSRepositoryConcepts.Model.rootNodes
    }

    override fun getChildren(link: IChildLink): Iterable<INode> {
        if (link is MPSChildLink) {
            DependencyTracking.accessed(MPSChildrenDependency(node, link.link))
        } else {
            DependencyTracking.accessed(MPSAllChildrenDependency(node))
        }
        return node.children.map { MPSNode(it) }.filter {
            it.getContainmentLink().conformsTo(link)
        }
    }

    override fun moveChild(role: IChildLink, index: Int, child: INode) {
        val link = getMPSContainmentLink(role)

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
        val link = getMPSContainmentLink(role)

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
        val targetConcept = concept?.let {
            MPSLanguageRepository(repo).resolveConcept(it.getUID())
                ?: MPSConcept.tryParseUID(it.getUID())
        }

        // A null value for the concept would default to BaseConcept, but then BaseConcept should be used explicitly.
        checkNotNull(targetConcept) { "MPS concept not found: $concept" }

        return addNewChild(role, index, targetConcept)
    }

    override fun addNewChildren(role: String?, index: Int, concepts: List<IConceptReference?>): List<INode> {
        requireNotNull(role) { "containment link required" }
        val link = MPSChildLink(getMPSContainmentLink(role))
        return addNewChildren(link, index, concepts)
    }

    override fun addNewChildren(link: IChildLink, index: Int, concepts: List<IConceptReference?>): List<INode> {
        return concepts.mapIndexed { i, it -> addNewChild(link, if (index >= 0) index + i else index, it) }
    }

    @Deprecated("use IChildLink instead of String")
    override fun addNewChild(role: String?, index: Int, concept: IConcept?): INode {
        requireNotNull(role) { "containment link required" }
        val link = MPSChildLink(getMPSContainmentLink(role))
        return addNewChild(link, index, concept)
    }

    @Deprecated("use IChildLink instead of String")
    override fun addNewChild(role: String?, index: Int, concept: IConceptReference?): INode {
        requireNotNull(role) { "containment link required" }
        val link = MPSChildLink(getMPSContainmentLink(role))
        return addNewChild(link, index, concept)
    }

    override fun getReferenceTarget(link: IReferenceLink): INode? {
        if (link is MPSReferenceLink) {
            DependencyTracking.accessed(MPSReferenceDependency(node, link.link))
        } else {
            DependencyTracking.accessed(MPSAllReferencesDependency(node))
        }
        return node.references.filter { MPSReferenceLink(it.link).getUID() == link.getUID() }
            .firstOrNull()?.targetNode?.let { MPSNode(it) }
    }

    override fun setReferenceTarget(link: IReferenceLink, target: INode?) {
        val refLink = when (link) {
            is MPSReferenceLink -> link.link
            else -> node.references.find { MPSReferenceLink(it.link).getUID() == link.getUID() }?.link
                ?: node.concept.referenceLinks.find { MPSReferenceLink(it).getUID() == link.getUID() }
                ?: SReferenceLinkAdapterById(SReferenceLinkId.deserialize(link.getUID()), "")
        }

        val targetNode = target?.let { getArea().resolveNode(it.reference) } as MPSNode
        node.setReferenceTarget(refLink, targetNode.node)
    }

    override fun setReferenceTarget(role: IReferenceLink, target: INodeReference?) {
        setReferenceTarget(role, target?.resolveIn(getArea()))
    }

    override fun getReferenceTargetRef(role: IReferenceLink): INodeReference? {
        if (role is MPSReferenceLink) {
            DependencyTracking.accessed(MPSReferenceDependency(node, role.link))
        } else {
            DependencyTracking.accessed(MPSAllReferencesDependency(node))
        }
        return node.references.firstOrNull { MPSReferenceLink(it.link).getUID() == role.getUID() }
            ?.targetNodeReference?.let { MPSNodeReference(it) }
    }

    override fun getPropertyValue(property: IProperty): String? {
        if (property is MPSProperty) {
            DependencyTracking.accessed(MPSPropertyDependency(node, property.property))
        } else {
            DependencyTracking.accessed(MPSAllPropertiesDependency(node))
        }
        val mpsProperty = node.properties.firstOrNull { MPSProperty(it).getUID() == property.getUID() } ?: return null
        return node.getProperty(mpsProperty)
    }

    override fun setPropertyValue(property: IProperty, value: String?) {
        val mpsProperty = when (property) {
            is MPSProperty -> property.property
            else -> node.properties.find { MPSProperty(it).getUID() == property.getUID() }
                ?: node.concept.properties.find { MPSProperty(it).getUID() == property.getUID() }
                ?: SPropertyAdapterById(SPropertyId.deserialize(property.getUID()), "")
        }
        node.setProperty(mpsProperty, value)
    }

    private fun getMPSContainmentLink(childLink: IChildLink): SContainmentLink = when (childLink) {
        is MPSChildLink -> childLink.link
        else -> getMPSContainmentLink(childLink.getUID())
    }

    private fun getMPSContainmentLink(uid: String): SContainmentLink {
        return node.concept.containmentLinks.find { MPSChildLink(it).getUID() == uid }
            ?: SContainmentLinkAdapterById(SContainmentLinkId.deserialize(uid), "")
    }
}
