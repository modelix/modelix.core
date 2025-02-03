package org.modelix.model.mpsadapters

import jetbrains.mps.project.MPSProject
import jetbrains.mps.project.ModuleId
import jetbrains.mps.project.ProjectBase
import jetbrains.mps.smodel.Generator
import jetbrains.mps.smodel.tempmodel.TempModule
import jetbrains.mps.smodel.tempmodel.TempModule2
import org.jetbrains.mps.openapi.module.SModule
import org.jetbrains.mps.openapi.module.SRepository
import org.modelix.model.api.BuiltinLanguages
import org.modelix.model.api.IChildLinkReference
import org.modelix.model.api.IConcept
import org.modelix.model.api.INode
import org.modelix.model.api.INodeReference
import org.modelix.model.api.IPropertyReference
import org.modelix.model.api.IReadableNode
import org.modelix.model.api.IReferenceLinkReference
import org.modelix.model.api.IWritableNode
import org.modelix.model.api.NullChildLinkReference
import org.modelix.mps.api.ModelixMpsApi

fun SRepository.asLegacyNode(): INode = MPSRepositoryAsNode(this).asLegacyNode()
fun SRepository.asWritableNode(): IWritableNode = MPSRepositoryAsNode(this)
fun SRepository.asReadableNode(): IReadableNode = MPSRepositoryAsNode(this)

data class MPSRepositoryAsNode(@get:JvmName("getRepository_") val repository: SRepository) : MPSGenericNodeAdapter<SRepository>() {

    companion object {
        private val propertyAccessors = listOf<Pair<IPropertyReference, IPropertyAccessor<SRepository>>>()
        private val referenceAccessors = listOf<Pair<IReferenceLinkReference, IReferenceAccessor<SRepository>>>()
        private val childAccessors = listOf<Pair<IChildLinkReference, IChildAccessor<SRepository>>>(
            BuiltinLanguages.MPSRepositoryConcepts.Repository.modules.toReference() to object : IChildAccessor<SRepository> {
                override fun read(element: SRepository): List<IWritableNode> {
                    return element.modules.filter { !it.isTempModule() && it !is Generator }.map { MPSModuleAsNode(it) }
                }

                override fun addNew(
                    element: SRepository,
                    index: Int,
                    sourceNode: SpecWithResolvedConcept,
                ): IWritableNode {
                    return when (sourceNode.getConceptReference()) {
                        BuiltinLanguages.MPSRepositoryConcepts.Solution.getReference() -> {
                            SolutionProducer(ModelixMpsApi.getMPSProject() as MPSProject).create(
                                sourceNode.getNode().getPropertyValue(BuiltinLanguages.jetbrains_mps_lang_core.INamedConcept.name.toReference())!!,
                                sourceNode.getNode().getPropertyValue(BuiltinLanguages.MPSRepositoryConcepts.Module.id.toReference())!!.let { ModuleId.fromString(it) },
                            ).let { MPSModuleAsNode(it) }
                        }
                        BuiltinLanguages.MPSRepositoryConcepts.Language.getReference() -> {
                            LanguageProducer(ModelixMpsApi.getMPSProject() as MPSProject).create(
                                sourceNode.getNode().getPropertyValue(BuiltinLanguages.jetbrains_mps_lang_core.INamedConcept.name.toReference())!!,
                                sourceNode.getNode().getPropertyValue(BuiltinLanguages.MPSRepositoryConcepts.Module.id.toReference())!!.let { ModuleId.fromString(it) },
                            ).let { MPSModuleAsNode(it) }
                        }
                        BuiltinLanguages.MPSRepositoryConcepts.DevKit.getReference() -> {
                            DevkitProducer(ModelixMpsApi.getMPSProject() as MPSProject).create(
                                sourceNode.getNode().getPropertyValue(BuiltinLanguages.jetbrains_mps_lang_core.INamedConcept.name.toReference())!!,
                                sourceNode.getNode().getPropertyValue(BuiltinLanguages.MPSRepositoryConcepts.Module.id.toReference())!!.let { ModuleId.fromString(it) },
                            ).let { MPSModuleAsNode(it) }
                        }
                        else -> throw UnsupportedOperationException("Module type not supported yet: ${sourceNode.getConceptReference()}")
                    }
                }
            },
            BuiltinLanguages.MPSRepositoryConcepts.Repository.tempModules.toReference() to object : IChildAccessor<SRepository> {
                override fun read(element: SRepository): List<IWritableNode> {
                    return element.modules.filter { it.isTempModule() }.map { MPSModuleAsNode(it) }
                }
            },
            BuiltinLanguages.MPSRepositoryConcepts.Repository.projects.toReference() to object : IChildAccessor<SRepository> {
                override fun read(element: SRepository): List<IWritableNode> {
                    return ModelixMpsApi.getMPSProjects()
                        .map { MPSProjectAsNode(it as ProjectBase) }
                }
            },
        )
    }

    override fun getElement(): SRepository = repository

    override fun getRepository(): SRepository = repository

    override fun getPropertyAccessors() = propertyAccessors

    override fun getReferenceAccessors() = referenceAccessors

    override fun getChildAccessors() = childAccessors

    override fun getParent(): IWritableNode? = null

    override fun getNodeReference(): INodeReference = MPSRepositoryReference

    override fun getConcept(): IConcept = BuiltinLanguages.MPSRepositoryConcepts.Repository

    override fun getContainmentLink(): IChildLinkReference = NullChildLinkReference
}

private fun SModule.isTempModule(): Boolean = this is TempModule || this is TempModule2
