package org.modelix.mps.model.sync.bulk

import jetbrains.mps.project.MPSProject
import org.modelix.model.api.BuiltinLanguages
import org.modelix.model.api.IChildLinkReference
import org.modelix.model.api.INodeReference
import org.modelix.model.api.IReadableNode
import org.modelix.model.api.getStereotype
import org.modelix.model.mpsadapters.MPSModuleAsNode
import org.modelix.model.mpsadapters.MPSProjectAsNode
import org.modelix.model.sync.bulk.IModelMask

class MPSProjectSyncMask(
    val projects: List<MPSProject>,
    val isMPSSide: Boolean,
    val includedModules: Set<INodeReference>? = null,
    val excludedModules: Set<INodeReference>? = null,
) : IModelMask {

    override fun <T : IReadableNode> filterChildren(
        parent: IReadableNode,
        role: IChildLinkReference,
        children: List<T>,
    ): List<T> {
        return when (parent.getConceptReference()) {
            BuiltinLanguages.MPSRepositoryConcepts.Repository.getReference() -> when {
                role.matches(BuiltinLanguages.MPSRepositoryConcepts.Repository.tempModules.toReference()) -> emptyList()
                role.matches(BuiltinLanguages.MPSRepositoryConcepts.Repository.modules.toReference()) -> {
                    val included = includedModules ?: if (isMPSSide) {
                        projects.flatMap { it.projectModules }.map { MPSModuleAsNode(it).getNodeReference() }.toSet()
                    } else {
                        null
                    }
                    val excluded = excludedModules
                    children.filter {
                        val ref = it.getNodeReference()
                        included?.contains(ref) != false && excluded?.contains(ref) != true
                    }
                }
                role.matches(BuiltinLanguages.MPSRepositoryConcepts.Repository.projects.toReference()) -> {
                    val included = projects.map { MPSProjectAsNode(it).getNodeReference().serialize() }.toSet()
                    children.filter { included.contains(it.getNodeReference().serialize()) }
                }
                else -> children
            }
            BuiltinLanguages.MPSRepositoryConcepts.Project.getReference() -> when {
                else -> children
            }
            else -> when {
                role.matches(BuiltinLanguages.MPSRepositoryConcepts.Module.models.toReference()) -> {
                    children.filterNot { isStubModel(it.getStereotype()) }
                }
                else -> children
            }
        }
    }

    private fun isStubModel(stereotype: String?): Boolean {
        if (stereotype == null) return false
        return stereotype.contains("stub")
    }
}
