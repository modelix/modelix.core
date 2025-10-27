package org.modelix.model.mpsadapters

import jetbrains.mps.project.AbstractModule
import jetbrains.mps.project.ModuleId
import jetbrains.mps.smodel.Generator
import jetbrains.mps.smodel.Language
import jetbrains.mps.smodel.tempmodel.TempModule
import jetbrains.mps.smodel.tempmodel.TempModule2
import org.jetbrains.mps.openapi.model.EditableSModel
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
                            SolutionProducer(MPSProjectAsNode.getContextProject()).create(
                                sourceNode.getNode().getPropertyValue(BuiltinLanguages.jetbrains_mps_lang_core.INamedConcept.name.toReference())!!,
                                sourceNode.getNode().getPropertyValue(BuiltinLanguages.MPSRepositoryConcepts.Module.id.toReference())!!.let { ModuleId.fromString(it) },
                            ).let { MPSModuleAsNode(it) }
                        }
                        BuiltinLanguages.MPSRepositoryConcepts.Language.getReference() -> {
                            LanguageProducer(MPSProjectAsNode.getContextProject()).create(
                                sourceNode.getNode().getPropertyValue(BuiltinLanguages.jetbrains_mps_lang_core.INamedConcept.name.toReference())!!,
                                sourceNode.getNode().getPropertyValue(BuiltinLanguages.MPSRepositoryConcepts.Module.id.toReference())!!.let { ModuleId.fromString(it) },
                            ).let { MPSModuleAsNode(it) }
                        }
                        BuiltinLanguages.MPSRepositoryConcepts.DevKit.getReference() -> {
                            DevkitProducer(MPSProjectAsNode.getContextProject()).create(
                                sourceNode.getNode().getPropertyValue(BuiltinLanguages.jetbrains_mps_lang_core.INamedConcept.name.toReference())!!,
                                sourceNode.getNode().getPropertyValue(BuiltinLanguages.MPSRepositoryConcepts.Module.id.toReference())!!.let { ModuleId.fromString(it) },
                            ).let { MPSModuleAsNode(it) }
                        }
                        else -> throw UnsupportedOperationException("Module type not supported yet: ${sourceNode.getConceptReference()}")
                    }
                }

                override fun remove(element: SRepository, child: IWritableNode) {
                    (child as MPSModuleAsNode<*>).module.delete()
                }
            },
            BuiltinLanguages.MPSRepositoryConcepts.Repository.tempModules.toReference() to object : IChildAccessor<SRepository> {
                override fun read(element: SRepository): List<IWritableNode> {
                    return element.modules.filter { it.isTempModule() }.map { MPSModuleAsNode(it) }
                }

                override fun addNew(element: SRepository, index: Int, sourceNode: SpecWithResolvedConcept): IWritableNode {
                    throw UnsupportedOperationException("read only")
                }

                override fun remove(element: SRepository, child: IWritableNode) {
                    throw UnsupportedOperationException("read only")
                }
            },
            BuiltinLanguages.MPSRepositoryConcepts.Repository.projects.toReference() to object : IChildAccessor<SRepository> {
                override fun read(element: SRepository): List<IWritableNode> {
                    return MPSProjectAsNode.getContextProjects().map { MPSProjectAsNode(it) }
                }

                override fun addNew(element: SRepository, index: Int, sourceNode: SpecWithResolvedConcept): IWritableNode {
                    throw UnsupportedOperationException("read only")
                }

                override fun remove(element: SRepository, child: IWritableNode) {
                    throw UnsupportedOperationException("read only")
                }
            },
        )
    }

    override fun getElement(): SRepository = repository

    override fun getPropertyAccessors() = propertyAccessors

    override fun getReferenceAccessors() = referenceAccessors

    override fun getChildAccessors() = childAccessors

    override fun getParent(): IWritableNode? = null

    override fun getNodeReference(): INodeReference = MPSRepositoryReference

    override fun getConcept(): IConcept = BuiltinLanguages.MPSRepositoryConcepts.Repository

    override fun getContainmentLink(): IChildLinkReference = NullChildLinkReference
}

private fun SModule.isTempModule(): Boolean = this is TempModule || this is TempModule2

internal fun SModule.delete() {
    // Without saving first, MPS might detect a conflict that can result in data loss and prevents it.
    saveModuleAndModels()
    if (this is Generator) {
        val language = this.sourceLanguage().resolveSourceModule() as? Language
        if (language != null) {
            language.saveModuleAndModels()
            language.generators.forEach { it.saveModuleAndModels() }
        }
    }

    val projects = MPSProjectAsNode.getContextProjects()
    val project = projects.lastOrNull() { it.getModules().andGenerators().contains(this) } ?: projects.last()
    project.deleteModule(this)
}

private fun SModule.saveModuleAndModels() {
    (this as? AbstractModule)?.save()
    models.filterIsInstance<EditableSModel>().forEach { it.save() }
}

fun List<SModule>.andGenerators(): List<SModule> {
    return flatMap {
        when (it) {
            is Language -> listOf(it) + it.generators
            else -> listOf(it)
        }
    }
}
