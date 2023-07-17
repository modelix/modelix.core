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
import org.modelix.model.api.IChildLink
import org.modelix.model.api.IConcept
import org.modelix.model.api.IConceptReference
import org.modelix.model.api.IDeprecatedNodeDefaults
import org.modelix.model.api.INode
import org.modelix.model.api.INodeReference
import org.modelix.model.api.IProperty
import org.modelix.model.api.IReferenceLink
import org.modelix.model.api.SerializedNodeReference
import org.modelix.model.area.IArea

data class MPSRepositoryAsNode(val repository: SRepository) : IDeprecatedNodeDefaults {
    override fun getArea(): IArea {
        return MPSArea(repository)
    }

    override val isValid: Boolean
        get() = TODO("Not yet implemented")
    override val reference: INodeReference
        get() = SerializedNodeReference("mps-repository")
    override val concept: IConcept
        get() = RepositoryLanguage.Repository
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
        TODO("Not yet implemented")
    }

    override fun getChildren(link: IChildLink): Iterable<INode> {
        return if (link.getUID().endsWith("0a7577d1-d4e5-431d-98b1-fae38f9aee80/474657388638618902/474657388638618903") ||
            link.getUID().contains("modules") ||
            link.getSimpleName() == "modules"
        ) {
            repository.modules.map { MPSModuleAsNode(it) }
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
        TODO("Not yet implemented")
    }

    override fun setReferenceTarget(link: IReferenceLink, target: INode?) {
        TODO("Not yet implemented")
    }

    override fun setReferenceTarget(role: IReferenceLink, target: INodeReference?) {
        TODO("Not yet implemented")
    }

    override fun getReferenceTargetRef(role: IReferenceLink): INodeReference? {
        TODO("Not yet implemented")
    }

    override fun getPropertyValue(property: IProperty): String? {
        TODO("Not yet implemented")
    }

    override fun setPropertyValue(property: IProperty, value: String?) {
        TODO("Not yet implemented")
    }

    override fun getPropertyLinks(): List<IProperty> {
        TODO("Not yet implemented")
    }

    override fun getReferenceLinks(): List<IReferenceLink> {
        TODO("Not yet implemented")
    }
}
