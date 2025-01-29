package org.modelix.mps.model.sync.bulk

import jetbrains.mps.project.MPSProject
import org.modelix.model.api.BuiltinLanguages
import org.modelix.model.api.IChildLinkReference
import org.modelix.model.api.IReadableNode
import org.modelix.model.api.IWritableNode
import org.modelix.model.mpsadapters.MPSModuleAsNode
import org.modelix.model.mpsadapters.MPSProjectAsNode
import org.modelix.model.sync.bulk.ModelSynchronizer

class MPSProjectSyncFilter(val projects: List<MPSProject>, val toMPS: Boolean) : ModelSynchronizer.IFilter {

    private val fromMPS: Boolean get() = !toMPS

    override fun needsDescentIntoSubtree(subtreeRoot: IReadableNode): Boolean {
        return true
    }

    override fun needsSynchronization(node: IReadableNode): Boolean {
        return true
    }

    private fun <T : IReadableNode> filterChildren(
        parent: IReadableNode,
        role: IChildLinkReference,
        children: List<T>,
        isSourceChildren: Boolean,
    ): List<T> {
        val isMPSSide = fromMPS == isSourceChildren

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
                        val included = projects.map { MPSProjectAsNode(it).asWritableNode().getNodeReference().serialize() }.toSet()
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
            else -> children
        }
    }

    override fun filterSourceChildren(
        parent: IReadableNode,
        role: IChildLinkReference,
        children: List<IReadableNode>,
    ): List<IReadableNode> {
        return filterChildren(parent, role, children, true)
    }

    override fun filterTargetChildren(
        parent: IWritableNode,
        role: IChildLinkReference,
        children: List<IWritableNode>,
    ): List<IWritableNode> {
        return filterChildren(parent, role, children, false)
    }
}
