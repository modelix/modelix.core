package org.modelix.model.mpsadapters

import jetbrains.mps.ide.ThreadUtils
import jetbrains.mps.project.facets.JavaModuleFacet
import jetbrains.mps.smodel.GlobalModelAccess
import jetbrains.mps.smodel.ModelImports
import org.jetbrains.mps.openapi.model.SNodeReference
import org.jetbrains.mps.openapi.module.SRepository
import org.jetbrains.mps.openapi.persistence.PersistenceFacade
import org.modelix.model.api.BuiltinLanguages
import org.modelix.model.api.ChildLinkReferenceByUID
import org.modelix.model.api.IBranch
import org.modelix.model.api.IConcept
import org.modelix.model.api.IConceptReference
import org.modelix.model.api.INode
import org.modelix.model.api.INodeReference
import org.modelix.model.area.IArea
import org.modelix.model.area.IAreaListener
import org.modelix.model.area.IAreaReference
import org.modelix.mps.multiplatform.model.MPSDevKitDependencyReference
import org.modelix.mps.multiplatform.model.MPSJavaModuleFacetReference
import org.modelix.mps.multiplatform.model.MPSModelImportReference
import org.modelix.mps.multiplatform.model.MPSModelReference
import org.modelix.mps.multiplatform.model.MPSModuleDependencyReference
import org.modelix.mps.multiplatform.model.MPSModuleReference
import org.modelix.mps.multiplatform.model.MPSNodeReference
import org.modelix.mps.multiplatform.model.MPSProjectModuleReference
import org.modelix.mps.multiplatform.model.MPSProjectReference
import org.modelix.mps.multiplatform.model.MPSRepositoryReference
import org.modelix.mps.multiplatform.model.MPSSingleLanguageDependencyReference

data class MPSArea(val repository: SRepository) : IArea, IAreaReference {

    private fun resolveMPSModelReference(ref: INodeReference): INode? {
        if (ref is MPSModelReference) {
            return ref.toMPS().resolve(repository)?.let { MPSModelAsNode(it).asLegacyNode() }
        }

        val serialized = ref.serialize().substringAfter("${org.modelix.mps.multiplatform.model.MPSModelReference.PREFIX}:")
        val modelRef = MPSReferenceParser.parseSModelReference(serialized)

        return modelRef.resolve(repository)?.let { MPSModelAsNode(it).asLegacyNode() }
    }

    override fun getRoot(): INode {
        return repository.asLegacyNode()
    }

    @Deprecated("use ILanguageRepository.resolveConcept")
    override fun resolveConcept(ref: IConceptReference): IConcept? {
        return MPSLanguageRepository(repository).resolveConcept(ref.getUID())
    }

    override fun resolveNode(ref: INodeReference): INode? {
        // By far, the most common case is to resolve a MPSNodeReference.
        // Optimize for that case by not serializing and doing string operations.
        if (ref is MPSNodeReference) {
            return resolveSNodeReferenceToMPSNode(ref.toMPS())
        }
        val serialized = ref.serialize()
        val prefix = serialized.substringBefore(":")
        return when (prefix) {
            MPSModuleReference.PREFIX -> resolveMPSModuleReference(ref)?.asLegacyNode()
            MPSModelReference.PREFIX -> resolveMPSModelReference(ref)
            MPSNodeReference.PREFIX, "mps-node" -> resolveMPSNodeReference(ref) // mps-node for backwards compatibility
            MPSDevKitDependencyReference.PREFIX -> resolveMPSDevKitDependencyReference(ref)?.asLegacyNode()
            MPSJavaModuleFacetReference.PREFIX -> resolveMPSJavaModuleFacetReference(ref)?.asLegacyNode()
            MPSModelImportReference.PREFIX -> resolveMPSModelImportReference(ref)?.asLegacyNode()
            MPSModuleDependencyReference.PREFIX -> resolveMPSModuleDependencyReference(ref)?.asLegacyNode()
            MPSModuleReferenceReference.PREFIX -> resolveMPSModuleReferenceReference(ref)?.asLegacyNode()
            MPSProjectReference.PREFIX -> resolveMPSProjectReference(ref)?.asLegacyNode()
            MPSProjectModuleReference.PREFIX -> resolveMPSProjectModuleReference(ref)?.asLegacyNode()
            MPSSingleLanguageDependencyReference.PREFIX -> resolveMPSSingleLanguageDependencyReference(ref)?.asLegacyNode()
            MPSRepositoryReference.PREFIX -> resolveMPSRepositoryReference()
            else -> null
        }
    }

    override fun resolveOriginalNode(ref: INodeReference): INode? {
        return resolveNode(ref)
    }

    override fun resolveBranch(id: String): IBranch? {
        return null
    }

    override fun collectAreas(): List<IArea> {
        return listOf(this)
    }

    override fun getReference(): IAreaReference {
        return this
    }

    override fun resolveArea(ref: IAreaReference): IArea? {
        return takeIf { ref == it }
    }

    override fun <T> executeRead(f: () -> T): T {
        return repository.computeRead(f)
    }

    override fun <T> executeWrite(f: () -> T): T {
        var result: T? = null
        executeWrite({ result = f() }, enforceCommand = true)
        return result as T
    }

    fun executeWrite(f: () -> Unit, enforceCommand: Boolean) {
        // Try to execute a command instead of a write action if possible,
        // because write actions don't trigger an update of the MPS editor.

        // A command can only be executed on the EDT (Event Dispatch Thread/AWT Thread/UI Thread).
        // We could dispatch it to the EDT and wait for the result, but that increases the risk for deadlocks.
        // The caller is responsible for calling this method from the EDT if a command is desired.
        val inEDT = ThreadUtils.isInEDT()

        if (inEDT || enforceCommand) {
            val projects = MPSProjectAsNode.getContextProjects().filterIsInstance<MPSProjectAdapter>().map { it.mpsProject }
            val modelAccessCandidates = (listOf(repository.modelAccess) + projects.map { it.modelAccess }).asReversed()
            // GlobalModelAccess throws an Exception when trying to execute a command.
            // Only a ProjectModelAccess can execute a command.
            val modelAccess = modelAccessCandidates.firstOrNull { it !is GlobalModelAccess }

            if (modelAccess != null) {
                if (inEDT) {
                    modelAccess.executeCommand { f() }
                } else {
                    ThreadUtils.runInUIThreadAndWait {
                        modelAccess.executeCommand { f() }
                    }
                }
                return
            }
        }

        // For a write access any ModelAccess works.
        // If there is no ModelAccess that is not a GlobalModelAccess then there are probably no open projects and
        // there can't be any open editors, so the issues doesn't exist.
        repository.modelAccess.runWriteAction { f() }
    }

    override fun canRead(): Boolean {
        return repository.modelAccess.canRead()
    }

    override fun canWrite(): Boolean {
        return repository.modelAccess.canWrite()
    }

    override fun addListener(l: IAreaListener) {
        throw UnsupportedOperationException("Not implemented")
    }

    override fun removeListener(l: IAreaListener) {
        throw UnsupportedOperationException("Not implemented")
    }

    private fun resolveMPSModuleReference(ref: INodeReference): MPSModuleAsNode<*>? {
        return MPSModuleReference.tryConvert(ref)?.toMPS()?.resolve(repository)?.let { MPSModuleAsNode(it) }
    }

    private fun resolveMPSNodeReference(ref: INodeReference): INode? {
        return MPSNodeReference.tryConvert(ref)?.toMPS()?.let { resolveSNodeReferenceToMPSNode(it) }
    }

    private fun resolveSNodeReferenceToMPSNode(sNodeReference: SNodeReference): INode? {
        return sNodeReference.resolve(repository)?.let { MPSNode(it) }
    }

    private fun resolveMPSDevKitDependencyReference(ref: INodeReference): MPSDevKitDependencyAsNode? {
        val ref = MPSDevKitDependencyReference.tryConvert(ref) ?: return null
        val userModule = ref.userModule
        val userModel = ref.userModel
        return when {
            userModule != null -> userModule.toMPS().resolve(repository)
                ?.let { MPSModuleAsNode(it).findDevKitDependency(ref.usedModuleId.toMPS().moduleId) }
            userModel != null -> userModel.toMPS().resolve(repository)
                ?.let { MPSModelAsNode(it).findDevKitDependency(ref.usedModuleId.toMPS().moduleId) }
            else -> error("No importer found.")
        }
    }

    private fun resolveMPSJavaModuleFacetReference(ref: INodeReference): MPSJavaModuleFacetAsNode? {
        val ref = MPSJavaModuleFacetReference.tryConvert(ref) ?: return null
        val facet = ref.moduleReference.toMPS().resolve(repository)?.getFacetOfType(JavaModuleFacet.FACET_TYPE)
        return facet?.let { MPSJavaModuleFacetAsNode(it as JavaModuleFacet) }
    }

    private fun resolveMPSModelImportReference(ref: INodeReference): MPSModelImportAsNode? {
        val ref = MPSModelImportReference.tryConvert(ref) ?: return null

        val importingModel = ref.importingModel.toMPS().resolve(repository) ?: return null
        if (!ModelImports(importingModel).importedModels.contains(ref.importedModel.toMPS())) return null

        return MPSModelImportAsNode(importedModel = ref.importedModel.toMPS(), importingModel = importingModel)
    }

    private fun resolveMPSModuleDependencyReference(ref: INodeReference): MPSModuleDependencyAsNode? {
        val ref = MPSModuleDependencyReference.tryConvert(ref) ?: return null
        return ref.userModuleReference.toMPS().resolve(repository)
            ?.let { MPSModuleAsNode(it) }
            ?.findModuleDependency(ref.usedModuleId.toMPS().moduleId)
    }

    private fun resolveMPSModuleReferenceReference(ref: INodeReference): MPSModuleReferenceAsNode? {
        val ref = if (ref is MPSModuleReferenceReference) {
            ref
        } else {
            val parts = ref.serialize().substringAfter("${MPSModuleReferenceReference.PREFIX}:").split(MPSModuleReferenceReference.SEPARATOR)
            MPSModuleReferenceReference(
                PersistenceFacade.getInstance().createModuleId(parts[0].urlDecode()),
                ChildLinkReferenceByUID(parts[1].urlDecode()),
                PersistenceFacade.getInstance().createModuleId(parts[2]),
            )
        }

        val parent = MPSModuleAsNode(PersistenceFacade.getInstance().createModuleReference(ref.parent, "").resolve(repository) ?: return null)
        return parent.getChildren(ref.link).filterIsInstance<MPSModuleReferenceAsNode>()
            .find { it.target.moduleId == ref.target }
    }

    private fun resolveMPSProjectReference(ref: INodeReference): MPSProjectAsNode? {
        return MPSRepositoryAsNode(repository)
            .getChildren(BuiltinLanguages.MPSRepositoryConcepts.Repository.projects.toReference())
            .find { it.getNodeReference() == ref }
            ?.let { it as MPSProjectAsNode }
    }

    private fun resolveMPSProjectModuleReference(ref: INodeReference): MPSProjectModuleAsNode? {
        val ref = MPSProjectModuleReference.tryConvert(ref) ?: return null
        val project = resolveNode(ref.projectRef)?.asWritableNode() ?: return null
        return project
            .getChildren(BuiltinLanguages.MPSRepositoryConcepts.Project.projectModules.toReference())
            .find { it.getNodeReference() == ref }
            ?.let { it as MPSProjectModuleAsNode }
    }

    private fun resolveMPSSingleLanguageDependencyReference(ref: INodeReference): MPSSingleLanguageDependencyAsNode? {
        val ref = MPSSingleLanguageDependencyReference.tryConvert(ref) ?: return null
        val userModule = ref.userModule
        val userModel = ref.userModel
        return when {
            userModule != null -> userModule.toMPS().resolve(repository)
                ?.let { MPSModuleAsNode(it).findSingleLanguageDependency(ref.usedModuleId.toMPS().moduleId) }
            userModel != null -> userModel.toMPS().resolve(repository)
                ?.let { MPSModelAsNode(it).findSingleLanguageDependency(ref.usedModuleId.toMPS().moduleId) }
            else -> error("No importer found.")
        }
    }

    private fun resolveMPSRepositoryReference(): INode {
        return repository.asLegacyNode()
    }
}

fun <R> SRepository.computeRead(body: () -> R): R {
    var result: R? = null
    modelAccess.runReadAction {
        result = body()
    }
    return result as R
}
