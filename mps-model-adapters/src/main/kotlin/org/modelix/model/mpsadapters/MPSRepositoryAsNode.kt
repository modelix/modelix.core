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

import org.jetbrains.mps.openapi.module.SRepository
import org.modelix.model.api.BuiltinLanguages
import org.modelix.model.api.IChildLink
import org.modelix.model.api.IConcept
import org.modelix.model.api.IConceptReference
import org.modelix.model.api.IDeprecatedNodeDefaults
import org.modelix.model.api.INode
import org.modelix.model.api.INodeReference
import org.modelix.model.api.IProperty
import org.modelix.model.api.IReferenceLink
import org.modelix.model.api.NodeReference
import org.modelix.model.api.NullChildLink
import org.modelix.model.area.IArea

data class MPSRepositoryAsNode(val repository: SRepository) : IDeprecatedNodeDefaults {

    companion object {
        private val builtinRepository = BuiltinLanguages.MPSRepositoryConcepts.Repository
    }

    override fun getArea(): IArea {
        return MPSArea(repository)
    }

    override val isValid: Boolean
        get() = true
    override val reference: INodeReference
        get() = NodeReference("mps-repository")
    override val concept: IConcept
        get() = builtinRepository
    override val parent: INode?
        get() = null

    override fun getConceptReference(): IConceptReference? {
        return concept.getReference()
    }

    override val allChildren: Iterable<INode>
        get() = repository.modules.map { MPSModuleAsNode(it) }

    override fun removeChild(child: INode) {
        TODO("Not yet implemented")
    }

    override fun getContainmentLink(): IChildLink? {
        return null
    }

    override fun getChildren(link: IChildLink): Iterable<INode> {
        return if (link is NullChildLink) {
            return emptyList()
        } else if (link.conformsTo(builtinRepository.modules)) {
            repository.modules.map { MPSModuleAsNode(it) }
        } else if (link.conformsTo(builtinRepository.projects)) {
            TODO()
        } else {
            emptyList()
        }
    }

    override fun moveChild(role: IChildLink, index: Int, child: INode) {
        TODO("Not yet implemented")
    }

    override fun addNewChild(role: IChildLink, index: Int, concept: IConcept?): INode {
        TODO("Not yet implemented")
    }

    override fun addNewChild(role: IChildLink, index: Int, concept: IConceptReference?): INode {
        TODO("Not yet implemented")
    }

    override fun getReferenceTarget(link: IReferenceLink): INode? {
        throw UnsupportedOperationException("Concept $concept does not have references")
    }

    override fun setReferenceTarget(link: IReferenceLink, target: INode?) {
        if (target != null) {
            throw IllegalArgumentException("$concept doesn't contain a reference link $link")
        }
    }

    override fun setReferenceTarget(role: IReferenceLink, target: INodeReference?) {
        if (target != null) {
            throw IllegalArgumentException("$concept doesn't contain a reference link $role")
        }
    }

    override fun getReferenceTargetRef(role: IReferenceLink): INodeReference? {
        throw UnsupportedOperationException("Concept $concept does not have references")
    }

    override fun getPropertyValue(property: IProperty): String? {
        throw UnsupportedOperationException("Concept $concept does not have properties")
    }

    override fun setPropertyValue(property: IProperty, value: String?) {
        if (value != null) {
            throw IllegalArgumentException("$concept doesn't contain a property $property")
        }
    }

    override fun getPropertyLinks(): List<IProperty> {
        return emptyList() // A repository has no properties
    }

    override fun getReferenceLinks(): List<IReferenceLink> {
        return emptyList() // A repository has no references
    }
}
