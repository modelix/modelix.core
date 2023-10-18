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
import jetbrains.mps.smodel.ModelImports
import jetbrains.mps.smodel.adapter.structure.MetaAdapterFactory
import org.jetbrains.mps.openapi.model.SModel
import org.jetbrains.mps.openapi.module.SModuleId
import org.modelix.model.api.BuiltinLanguages
import org.modelix.model.api.IChildLink
import org.modelix.model.api.IConcept
import org.modelix.model.api.INode
import org.modelix.model.api.INodeReference
import org.modelix.model.api.IProperty
import org.modelix.model.api.NodeReference
import org.modelix.model.api.NullChildLink
import org.modelix.model.area.IArea

data class MPSModelAsNode(val model: SModel) : IDefaultNodeAdapter {

    companion object {
        fun wrap(model: SModel?): MPSModelAsNode? = model?.let { MPSModelAsNode(it) }
        private val builtinModel = BuiltinLanguages.MPSRepositoryConcepts.Model
    }

    override fun getArea(): IArea {
        return MPSArea(model.repository)
    }

    override val reference: INodeReference
        get() = NodeReference("mps-model:" + model.reference.toString())
    override val concept: IConcept
        get() = builtinModel
    override val parent: INode
        get() = MPSModuleAsNode(model.module)

    override val allChildren: Iterable<INode>
        get() {
            val childLinks = listOf(builtinModel.rootNodes, builtinModel.modelImports, builtinModel.usedLanguages)
            return childLinks.flatMap { getChildren(it) }
        }

    override fun removeChild(child: INode) {
        val link = child.getContainmentLink() ?: throw RuntimeException("ContainmentLink not found for node $child")
        if (link.conformsTo(builtinModel.usedLanguages)) {
            removeUsedLanguage(child)
        }
        super.removeChild(child)
    }

    private fun removeUsedLanguage(languageNode: INode) {
        check(model is SModelDescriptorStub) { "Model '$model' is not a SModelDescriptor." }
        check(languageNode is MPSSingleLanguageDependencyAsNode) { "Node $languageNode to be removed is not a single language dependency." }

        val languageToRemove = languageNode.moduleReference?.let { MetaAdapterFactory.getLanguage(it) }
        checkNotNull(languageToRemove) { "Language to be removed could not be found." }
        model.deleteLanguageId(languageToRemove)
    }

    override fun getContainmentLink(): IChildLink {
        return BuiltinLanguages.MPSRepositoryConcepts.Module.models
    }

    override fun getChildren(link: IChildLink): Iterable<INode> {
        return if (link is NullChildLink) {
            emptyList()
        } else if (link.conformsTo(builtinModel.rootNodes)) {
            model.rootNodes.map { MPSNode(it) }
        } else if (link.conformsTo(builtinModel.modelImports)) {
            ModelImports(model).importedModels.mapNotNull {
                MPSModelImportAsNode(it.resolve(model.repository), model)
            }
        } else if (link.conformsTo(builtinModel.usedLanguages)) {
            getImportedLanguagesAndDevKits()
        } else {
            emptyList()
        }
    }

    private fun getImportedLanguagesAndDevKits(): List<INode> {
        if (model !is SModelDescriptorStub) return emptyList()

        val importedLanguagesAndDevKits = mutableListOf<INode>()
        importedLanguagesAndDevKits.addAll(
            model.importedLanguageIds().filter { it.sourceModuleReference != null }.map {
                MPSSingleLanguageDependencyAsNode(
                    it.sourceModuleReference,
                    model.getLanguageImportVersion(it),
                    modelImporter = model,
                )
            },
        )
        importedLanguagesAndDevKits.addAll(
            model.importedDevkits().map { DevKitDependencyAsNode(it, model) },
        )
        return importedLanguagesAndDevKits
    }

    override fun getPropertyValue(property: IProperty): String? {
        return if (property.conformsTo(BuiltinLanguages.jetbrains_mps_lang_core.INamedConcept.name)) {
            model.name.value
        } else if (property.conformsTo(builtinModel.id)) {
            model.modelId.toString()
        } else if (property.isIdProperty()) {
            reference.serialize()
        } else if (property.conformsTo(builtinModel.stereotype)) {
            model.name.stereotype
        } else {
            null
        }
    }

    fun findSingleLanguageDependency(dependencyId: SModuleId): MPSSingleLanguageDependencyAsNode? {
        if (model is SModelDescriptorStub) {
            model.importedLanguageIds().forEach { entry ->
                if (entry.sourceModule?.moduleId == dependencyId) {
                    return MPSSingleLanguageDependencyAsNode(
                        entry.sourceModuleReference,
                        model.getLanguageImportVersion(entry),
                        modelImporter = model,
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
