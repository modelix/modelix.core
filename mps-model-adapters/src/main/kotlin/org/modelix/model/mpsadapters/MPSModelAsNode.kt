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

import jetbrains.mps.extapi.model.SModelDescriptorStub
import org.jetbrains.mps.openapi.model.SModel
import org.jetbrains.mps.openapi.module.SModuleId
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
import org.modelix.model.data.NodeData

data class MPSModelAsNode(val model: SModel) : IDeprecatedNodeDefaults {

    companion object {
        fun wrap(model: SModel?): MPSModelAsNode? = model?.let { MPSModelAsNode(it) }
    }

    override fun getArea(): IArea {
        return MPSArea(model.repository)
    }

    override val isValid: Boolean
        get() = TODO("Not yet implemented")
    override val reference: INodeReference
        get() = NodeReference("mps-model:" + model.reference.toString())
    override val concept: IConcept
        get() = RepositoryLanguage.Model
    override val parent: INode
        get() = MPSModuleAsNode(model.module)

    override fun getConceptReference(): IConceptReference {
        return concept.getReference()
    }

    override val allChildren: Iterable<INode>
        get() = model.rootNodes.map { MPSNode(it) }

    override fun removeChild(child: INode) {
        TODO("Not yet implemented")
    }

    override fun getContainmentLink(): IChildLink {
        return BuiltinLanguages.MPSRepositoryConcepts.Module.models
    }

    override fun getChildren(link: IChildLink): Iterable<INode> {
        return if (link is NullChildLink) {
            emptyList()
        } else if (link.getUID().endsWith(BuiltinLanguages.MPSRepositoryConcepts.Model.rootNodes.getUID()) ||
            link.getUID().contains("rootNodes") ||
            link.getSimpleName() == "rootNodes"
        ) {
            model.rootNodes.map { MPSNode(it) }
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
        return if (property.getUID().endsWith(BuiltinLanguages.jetbrains_mps_lang_core.INamedConcept.name.getUID()) ||
            property.getUID().contains("name") ||
            property.getSimpleName() == "name"
        ) {
            model.name.value
        } else if (property.getSimpleName() == NodeData.idPropertyKey) {
            model.modelId.toString()
        } else {
            null
        }
    }

    override fun setPropertyValue(property: IProperty, value: String?) {
        if (getPropertyValue(property) != value) {
            throw UnsupportedOperationException("Property $property of $concept is read-only")
        }
    }

    override fun getPropertyLinks(): List<IProperty> {
        return concept.getAllProperties()
    }

    override fun getReferenceLinks(): List<IReferenceLink> {
        return concept.getAllReferenceLinks()
    }

    fun findSingleLanguageDependency(dependencyId: SModuleId): SingleLanguageDependencyAsNode? {
        if (model is SModelDescriptorStub) {
            model.importedLanguageIds().forEach { entry ->
                if (entry.sourceModule?.moduleId == dependencyId) {
                    return SingleLanguageDependencyAsNode(
                        entry.sourceModuleReference,
                        model.getLanguageImportVersion(entry),
                        model,
                    )
                }
            }
        }
        return null
    }

    fun findDevKitDependency(dependencyId: SModuleId): DevKitDependencyAsNode? {
        if (model is SModelDescriptorStub) {
            model.importedDevkits().forEach { devKit ->
                if (devKit.moduleId == dependencyId) {
                    return DevKitDependencyAsNode(devKit, model)
                }
            }
        }
        return null
    }
}
