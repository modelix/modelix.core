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
import org.modelix.model.api.NullChildLink
import org.modelix.model.area.IArea

data class MPSModelAsNode(val model: SModel) : IDefaultNodeAdapter {

    private val childrenAccessors: Map<IChildLink, () -> Iterable<INode>> = mapOf(
        BuiltinLanguages.MPSRepositoryConcepts.Model.rootNodes to { model.rootNodes.map { MPSNode(it) } },
        BuiltinLanguages.MPSRepositoryConcepts.Model.modelImports to {
            ModelImports(model).importedModels.mapNotNull { modelRef ->
                val target = modelRef.resolve(model.repository)
                target?.let { MPSModelImportAsNode(it, model) }
            }
        },
        BuiltinLanguages.MPSRepositoryConcepts.Model.usedLanguages to { getImportedLanguagesAndDevKits() },
    )

    override fun getArea(): IArea {
        return MPSArea(model.repository)
    }

    override val reference: INodeReference
        get() = MPSModelReference(model.reference)
    override val concept: IConcept
        get() = BuiltinLanguages.MPSRepositoryConcepts.Model
    override val parent: INode
        get() = MPSModuleAsNode(model.module)

    override val allChildren: Iterable<INode>
        get() = childrenAccessors.values.flatMap { it() }

    override fun removeChild(child: INode) {
        val link = child.getContainmentLink() ?: error("ContainmentLink not found for node $child")
        if (link.conformsTo(BuiltinLanguages.MPSRepositoryConcepts.Model.usedLanguages)) {
            removeUsedLanguage(child)
        }
        super.removeChild(child)
    }

    private fun removeUsedLanguage(languageNode: INode) {
        check(model is SModelDescriptorStub) { "Model '$model' is not a SModelDescriptor." }
        check(languageNode is MPSSingleLanguageDependencyAsNode) { "Node $languageNode to be removed is not a single language dependency." }

        val languageToRemove = MetaAdapterFactory.getLanguage(languageNode.moduleReference)
        model.deleteLanguageId(languageToRemove)
    }

    override fun getContainmentLink(): IChildLink {
        return BuiltinLanguages.MPSRepositoryConcepts.Module.models
    }

    override fun getChildren(link: IChildLink): Iterable<INode> {
        if (link is NullChildLink) return emptyList()
        for (childrenAccessor in childrenAccessors) {
            if (link.conformsTo(childrenAccessor.key)) return childrenAccessor.value()
        }
        return emptyList()
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
            model.importedDevkits().map { MPSDevKitDependencyAsNode(it, modelImporter = model) },
        )
        return importedLanguagesAndDevKits
    }

    override fun getPropertyValue(property: IProperty): String? {
        return if (property.conformsTo(BuiltinLanguages.jetbrains_mps_lang_core.INamedConcept.name)) {
            model.name.value
        } else if (property.conformsTo(BuiltinLanguages.MPSRepositoryConcepts.Model.id)) {
            model.modelId.toString()
        } else if (property.conformsTo(BuiltinLanguages.MPSRepositoryConcepts.Model.stereotype)) {
            model.name.stereotype
        } else {
            null
        }
    }

    internal fun findSingleLanguageDependency(dependencyId: SModuleId): MPSSingleLanguageDependencyAsNode? {
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

    internal fun findDevKitDependency(dependencyId: SModuleId): MPSDevKitDependencyAsNode? {
        if (model is SModelDescriptorStub) {
            model.importedDevkits().forEach { devKit ->
                if (devKit.moduleId == dependencyId) {
                    return MPSDevKitDependencyAsNode(devKit, modelImporter = model)
                }
            }
        }
        return null
    }
}
