package org.modelix.model.mpsadapters

import jetbrains.mps.extapi.model.SModelDescriptorStub
import jetbrains.mps.project.ModuleId
import jetbrains.mps.smodel.ModelImports
import jetbrains.mps.smodel.adapter.ids.SLanguageId
import jetbrains.mps.smodel.adapter.structure.MetaAdapterFactory
import jetbrains.mps.smodel.adapter.structure.language.SLanguageAdapterById
import org.jetbrains.mps.openapi.model.SModel
import org.jetbrains.mps.openapi.module.SModuleId
import org.jetbrains.mps.openapi.module.SRepository
import org.jetbrains.mps.openapi.persistence.PersistenceFacade
import org.modelix.model.api.BuiltinLanguages
import org.modelix.model.api.IChildLinkReference
import org.modelix.model.api.IConcept
import org.modelix.model.api.INodeReference
import org.modelix.model.api.IPropertyReference
import org.modelix.model.api.IReferenceLinkReference
import org.modelix.model.api.IWritableNode
import org.modelix.model.data.asData
import java.util.UUID

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
                override fun addNew(
                    element: SModel,
                    index: Int,
                    sourceNode: SpecWithResolvedConcept,
                ): IWritableNode {
                    val nodeId = sourceNode.spec?.preferredNodeReference
                        ?.let { MPSNodeReference.tryConvert(it) }?.ref?.nodeId
                    return element.createNode(sourceNode.concept, nodeId)
                        .also { element.addRootNode(it) }
                        .let { MPSWritableNode(it) }
                }
            },
            BuiltinLanguages.MPSRepositoryConcepts.Model.modelImports.toReference() to object : IChildAccessor<SModel> {
                override fun read(element: SModel): List<IWritableNode> {
                    return ModelImports(element).importedModels.mapNotNull { modelRef ->
                        MPSModelImportAsNode(modelRef, element)
                    }
                }

                override fun addNew(
                    element: SModel,
                    index: Int,
                    sourceNode: SpecWithResolvedConcept,
                ): IWritableNode {
                    require(sourceNode.getConceptReference() == BuiltinLanguages.MPSRepositoryConcepts.ModelReference.getReference())

                    val importedModel = checkNotNull(sourceNode.getNode().getReferenceTarget(BuiltinLanguages.MPSRepositoryConcepts.ModelReference.model.toReference())) {
                        "Model reference not set: ${sourceNode.getNode().asLegacyNode().asData().toJson()}"
                    }
                    val modelId = checkNotNull(importedModel.getPropertyValue(BuiltinLanguages.MPSRepositoryConcepts.Model.id.toReference())) {
                        "Target model has no ID: $importedModel"
                    }
                    val modelName = importedModel.getPropertyValue(BuiltinLanguages.jetbrains_mps_lang_core.INamedConcept.name.toReference()) ?: ""
                    val moduleRef = importedModel.getParent()?.let { importedModule ->
                        val moduleId = importedModule.getPropertyValue(BuiltinLanguages.MPSRepositoryConcepts.Module.id.toReference()) ?: return@let null
                        val moduleName = importedModule.getPropertyValue(BuiltinLanguages.jetbrains_mps_lang_core.INamedConcept.name.toReference()) ?: ""
                        PersistenceFacade.getInstance().createModuleReference(ModuleId.fromString(moduleId), moduleName)
                    }
                    val modelRef = PersistenceFacade.getInstance().createModelReference(moduleRef, PersistenceFacade.getInstance().createModelId(modelId), modelName)
                    ModelImports(element).addModelImport(modelRef)
                    return MPSModelImportAsNode(modelRef, element)
                }
            },
            BuiltinLanguages.MPSRepositoryConcepts.Model.usedLanguages.toReference() to object : IChildAccessor<SModel> {
                override fun read(element: SModel): List<IWritableNode> {
                    if (element !is SModelDescriptorStub) return emptyList()

                    return element.importedLanguageIds().filter { it.sourceModuleReference != null }.map {
                        MPSSingleLanguageDependencyAsNode(
                            it,
                            modelImporter = element,
                        )
                    } + element.importedDevkits().map {
                        MPSDevKitDependencyAsNode(it, modelImporter = element).asWritableNode()
                    }
                }

                override fun addNew(
                    element: SModel,
                    index: Int,
                    sourceNode: SpecWithResolvedConcept,
                ): IWritableNode {
                    require(element is SModelDescriptorStub)
                    return when (sourceNode.getConceptReference()) {
                        BuiltinLanguages.MPSRepositoryConcepts.SingleLanguageDependency.getReference() -> {
                            val id = sourceNode.getNode().getPropertyValue(BuiltinLanguages.MPSRepositoryConcepts.LanguageDependency.uuid.toReference())
                            val name = sourceNode.getNode().getPropertyValue(BuiltinLanguages.MPSRepositoryConcepts.LanguageDependency.name.toReference())
                            val slang = SLanguageAdapterById(SLanguageId(UUID.fromString(id)), name ?: "")
                            element.addLanguage(slang)
                            return MPSSingleLanguageDependencyAsNode(
                                slang,
                                modelImporter = element,
                            )
                        }
                        BuiltinLanguages.MPSRepositoryConcepts.DevkitDependency.getReference() -> {
                            val id = sourceNode.getNode().getPropertyValue(BuiltinLanguages.MPSRepositoryConcepts.LanguageDependency.uuid.toReference())
                            val name = sourceNode.getNode().getPropertyValue(BuiltinLanguages.MPSRepositoryConcepts.LanguageDependency.name.toReference())
                            val ref = PersistenceFacade.getInstance().createModuleReference(ModuleId.regular(UUID.fromString(id)), name ?: "")
                            element.addDevKit(ref)
                            return MPSDevKitDependencyAsNode(
                                moduleReference = ref,
                                modelImporter = element,
                            ).asWritableNode()
                        }
                        else -> throw UnsupportedOperationException("Unsupported type: ${sourceNode.getConceptReference()}")
                    }
                }

                override fun remove(element: SModel, child: IWritableNode) {
                    check(element is SModelDescriptorStub) { "Model '$element' is not a SModelDescriptor." }
                    require(child is MPSSingleLanguageDependencyAsNode) { "Node $child to be removed is not a single language dependency." }
                    val languageToRemove = MetaAdapterFactory.getLanguage(child.moduleReference.sourceModuleReference)
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
                if (entry.sourceModuleReference.moduleId == dependencyId) {
                    return MPSSingleLanguageDependencyAsNode(
                        entry,
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
