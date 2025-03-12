package org.modelix.mps.model.sync.bulk

import jetbrains.mps.project.MPSProject
import org.modelix.model.api.BuiltinLanguages
import org.modelix.model.api.IChildLinkReference
import org.modelix.model.api.IReadableNode
import org.modelix.model.api.getName
import org.modelix.model.mpsadapters.MPSModuleAsNode
import org.modelix.model.mpsadapters.MPSProjectAsNode
import org.modelix.model.sync.bulk.IModelMask

class MPSProjectSyncMask(val projects: List<MPSProject>, val isMPSSide: Boolean) : IModelMask {

    override fun <T : IReadableNode> filterChildren(
        parent: IReadableNode,
        role: IChildLinkReference,
        children: List<T>,
    ): List<T> {
        return when (parent.getConceptReference()) {
            BuiltinLanguages.MPSRepositoryConcepts.Repository.getReference() -> when {
                role.matches(BuiltinLanguages.MPSRepositoryConcepts.Repository.tempModules.toReference()) -> emptyList()
                role.matches(BuiltinLanguages.MPSRepositoryConcepts.Repository.modules.toReference()) -> {
                    if (isMPSSide) {
                        val included = projects.flatMap { it.projectModules }.map { MPSModuleAsNode(it).getNodeReference().serialize() }.toSet()
                        children.filter { included.contains(it.getNodeReference().serialize()) }
                    } else {
                        children
                    }
                }
                role.matches(BuiltinLanguages.MPSRepositoryConcepts.Repository.projects.toReference()) -> {
                    if (isMPSSide) {
                        val included = projects.map { MPSProjectAsNode(it).getNodeReference().serialize() }.toSet()
                        children.filter { included.contains(it.getNodeReference().serialize()) }
                    } else {
                        children
                    }
                }
                else -> children
            }
            BuiltinLanguages.MPSRepositoryConcepts.Project.getReference() -> when {
                else -> children
            }
            else -> when {
                role.matches(BuiltinLanguages.MPSRepositoryConcepts.Module.models.toReference()) -> {
                    children.filterNot { isStubModel(it.getName()) }
                }
                else -> children
            }
        }
    }

    private fun isStubModel(name: String?): Boolean {
        if (name == null) return false
        val stereotype = name.substringAfter('@')
        return stereotype.contains("stub")
    }
}
