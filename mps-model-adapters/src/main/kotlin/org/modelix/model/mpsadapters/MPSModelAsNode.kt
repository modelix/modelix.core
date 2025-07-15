package org.modelix.model.mpsadapters

import jetbrains.mps.extapi.model.SModelDescriptorStub
import jetbrains.mps.extapi.persistence.FileDataSource
import jetbrains.mps.project.ModuleId
import jetbrains.mps.smodel.ModelImports
import jetbrains.mps.smodel.adapter.ids.SLanguageId
import jetbrains.mps.smodel.adapter.structure.MetaAdapterFactory
import jetbrains.mps.smodel.adapter.structure.language.SLanguageAdapterById
import org.jetbrains.mps.openapi.model.EditableSModel
import org.jetbrains.mps.openapi.model.SModel
import org.jetbrains.mps.openapi.model.SModelId
import org.jetbrains.mps.openapi.model.SModelName
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

data class MPSModelAsNode(val model: SModel) : MPSGenericNodeAdapter<SModel>() {

    companion object {
        private val propertyAccessors = listOf<Pair<IPropertyReference, IPropertyAccessor<SModel>>>(
            BuiltinLanguages.jetbrains_mps_lang_core.INamedConcept.name.toReference() to object : IPropertyAccessor<SModel> {
                override fun read(element: SModel): String? = element.name.value
                override fun write(element: SModel, value: String?) {
                    require(value != null) { "Model name cannot be null" }
                    element.rename(value)
                }
            },
            BuiltinLanguages.MPSRepositoryConcepts.Model.id.toReference() to object : IPropertyAccessor<SModel> {
                override fun read(element: SModel): String? = element.modelId.toString()
                override fun write(element: SModel, value: String?) {
                    throw UnsupportedOperationException("read only")
                }
            },
            BuiltinLanguages.MPSRepositoryConcepts.Model.stereotype.toReference() to object : IPropertyAccessor<SModel> {
                override fun read(element: SModel): String? = element.name.stereotype
                override fun write(element: SModel, value: String?) {
                    val oldName = element.name
                    element.rename(SModelName(oldName.longName, value).value)
                }
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
                    return element.createNode(sourceNode.concept, sourceNode.spec?.getPreferredSNodeId(element.reference))
                        .also {
                            it.copyNameFrom(sourceNode.spec)
                            element.addRootNode(it)
                        }
                        .let { MPSWritableNode(it) }
                }

                override fun remove(element: SModel, child: IWritableNode) {
                    element.removeRootNode((child as MPSWritableNode).node)
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
                    val smodelId: SModelId = PersistenceFacade.getInstance().createModelId(modelId)
                    val modelRef = PersistenceFacade.getInstance().createModelReference(
                        moduleRef.takeIf { !smodelId.isGloballyUnique },
                        smodelId,
                        modelName,
                    )
                    ModelImports(element).addModelImport(modelRef)
                    return MPSModelImportAsNode(modelRef, element)
                }

                override fun remove(
                    element: SModel,
                    child: IWritableNode,
                ) {
                    ModelImports(element).removeModelImport((child as MPSModelImportAsNode).importedModel)
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
                        MPSDevKitDependencyAsNode(it, modelImporter = element)
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
                            val slang = SLanguageAdapterById(SLanguageId.deserialize(id), name ?: "")
                            element.addLanguage(slang)
                            return MPSSingleLanguageDependencyAsNode(
                                slang,
                                modelImporter = element,
                            )
                        }
                        BuiltinLanguages.MPSRepositoryConcepts.DevkitDependency.getReference() -> {
                            val id = requireNotNull(sourceNode.getNode().getPropertyValue(BuiltinLanguages.MPSRepositoryConcepts.LanguageDependency.uuid.toReference())) {
                                "Has no ID: $sourceNode"
                            }
                            val name = sourceNode.getNode().getPropertyValue(BuiltinLanguages.MPSRepositoryConcepts.LanguageDependency.name.toReference())
                            val ref = PersistenceFacade.getInstance().createModuleReference(ModuleId.fromString(id), name ?: "")
                            element.addDevKit(ref)
                            return MPSDevKitDependencyAsNode(
                                moduleReference = ref,
                                modelImporter = element,
                            )
                        }
                        else -> throw UnsupportedOperationException("Unsupported type: ${sourceNode.getConceptReference()}")
                    }
                }

                override fun remove(element: SModel, child: IWritableNode) {
                    check(element is SModelDescriptorStub) { "Model '$element' is not a SModelDescriptor." }
                    when (child) {
                        is MPSSingleLanguageDependencyAsNode -> {
                            val languageToRemove = MetaAdapterFactory.getLanguage(child.moduleReference.sourceModuleReference)
                            element.deleteLanguageId(languageToRemove)
                        }
                        is MPSDevKitDependencyAsNode -> {
                            element.deleteDevKit(child.moduleReference)
                        }
                        else -> throw UnsupportedOperationException("Unsupported type: ${child.getConceptReference()}")
                    }
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

    override fun isReadOnly(): Boolean {
        return model.isReadOnly
    }

    override fun getParent(): IWritableNode? {
        return model.module?.let { MPSModuleAsNode(it) }
    }

    override fun getNodeReference(): INodeReference {
        return model.reference.toModelix()
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

private fun SModel.rename(newName: String) {
    (this as EditableSModel).rename(newName, this.source is FileDataSource)
}
