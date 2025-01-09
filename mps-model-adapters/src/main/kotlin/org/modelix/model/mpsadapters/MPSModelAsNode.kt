package org.modelix.model.mpsadapters

import jetbrains.mps.extapi.model.SModelDescriptorStub
import jetbrains.mps.smodel.ModelImports
import jetbrains.mps.smodel.adapter.structure.MetaAdapterFactory
import org.jetbrains.mps.openapi.model.SModel
import org.jetbrains.mps.openapi.module.SModuleId
import org.jetbrains.mps.openapi.module.SRepository
import org.modelix.model.api.BuiltinLanguages
import org.modelix.model.api.IChildLinkReference
import org.modelix.model.api.IConcept
import org.modelix.model.api.INodeReference
import org.modelix.model.api.IPropertyReference
import org.modelix.model.api.IReferenceLinkReference
import org.modelix.model.api.IWritableNode

data class MPSModelAsNode(val model: SModel) : MPSGenericNodeAdapter<SModel>() {

    companion object {
        private val propertyAccessors = listOf<Pair<IPropertyReference, IPropertyAccessor<SModel>>>(
            BuiltinLanguages.jetbrains_mps_lang_core.INamedConcept.name.toReference() to object : IPropertyAccessor<SModel> {
                override fun read(element: SModel): String? = element.name.value
            },
            BuiltinLanguages.MPSRepositoryConcepts.Model.id.toReference() to object : IPropertyAccessor<SModel> {
                override fun read(element: SModel): String? = element.modelId.toString()
            },
            BuiltinLanguages.MPSRepositoryConcepts.Model.stereotype.toReference() to object : IPropertyAccessor<SModel> {
                override fun read(element: SModel): String? = element.name.stereotype
            },
        )
        private val referenceAccessors = listOf<Pair<IReferenceLinkReference, IReferenceAccessor<SModel>>>()
        private val childAccessors = listOf<Pair<IChildLinkReference, IChildAccessor<SModel>>>(
            BuiltinLanguages.MPSRepositoryConcepts.Model.rootNodes.toReference() to object : IChildAccessor<SModel> {
                override fun read(element: SModel): List<IWritableNode> = element.rootNodes.map { MPSWritableNode(it) }
            },
            BuiltinLanguages.MPSRepositoryConcepts.Model.modelImports.toReference() to object : IChildAccessor<SModel> {
                override fun read(element: SModel): List<IWritableNode> = ModelImports(element).importedModels.mapNotNull { modelRef ->
                    val target = modelRef.resolve(element.repository)
                    target?.let { MPSModelImportAsNode(it, element).asWritableNode() }
                }
            },
            BuiltinLanguages.MPSRepositoryConcepts.Model.usedLanguages.toReference() to object : IChildAccessor<SModel> {
                override fun read(element: SModel): List<IWritableNode> {
                    if (element !is SModelDescriptorStub) return emptyList()

                    return element.importedLanguageIds().filter { it.sourceModuleReference != null }.map {
                        MPSSingleLanguageDependencyAsNode(
                            it.sourceModuleReference,
                            element.getLanguageImportVersion(it),
                            modelImporter = element,
                        ).asWritableNode()
                    } + element.importedDevkits().map {
                        MPSDevKitDependencyAsNode(it, modelImporter = element).asWritableNode()
                    }
                }

                override fun remove(element: SModel, child: IWritableNode) {
                    check(element is SModelDescriptorStub) { "Model '$element' is not a SModelDescriptor." }
                    require(child is MPSSingleLanguageDependencyAsNode) { "Node $child to be removed is not a single language dependency." }
                    val languageToRemove = MetaAdapterFactory.getLanguage(child.moduleReference)
                    element.deleteLanguageId(languageToRemove)
                }
            },
        )
    }

    override fun getRepository(): SRepository? {
        return model.module?.repository
    }

    override fun getElement(): SModel {
        return model
    }

    override fun getPropertyAccessors() = propertyAccessors

    override fun getReferenceAccessors() = referenceAccessors

    override fun getChildAccessors() = childAccessors

    override fun getParent(): IWritableNode? {
        return model.module?.let { MPSModuleAsNode(it) }
    }

    override fun getNodeReference(): INodeReference {
        return MPSModelReference(model.reference)
    }

    override fun getConcept(): IConcept {
        return BuiltinLanguages.MPSRepositoryConcepts.Model
    }

    override fun getContainmentLink(): IChildLinkReference {
        return BuiltinLanguages.MPSRepositoryConcepts.Module.models.toReference()
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
