package org.modelix.model.mpsadapters

import jetbrains.mps.project.ProjectBase
import jetbrains.mps.smodel.MPSModuleRepository
import org.modelix.model.api.BuiltinLanguages
import org.modelix.model.api.IChildLink
import org.modelix.model.api.IConcept
import org.modelix.model.api.INode
import org.modelix.model.api.INodeReference
import org.modelix.model.api.IProperty
import org.modelix.model.area.IArea

data class MPSProjectAsNode(val project: ProjectBase) : IDefaultNodeAdapter {

    override val reference: INodeReference
        get() = MPSProjectReference(project.name)
    override val concept: IConcept
        get() = BuiltinLanguages.MPSRepositoryConcepts.Project
    override val parent: INode
        get() = MPSRepositoryAsNode(MPSModuleRepository.getInstance())

    override val allChildren: Iterable<INode>
        get() = getChildren(BuiltinLanguages.MPSRepositoryConcepts.Project.projectModules)

    override fun getChildren(link: IChildLink): Iterable<INode> {
        if (link.conformsTo(BuiltinLanguages.MPSRepositoryConcepts.Project.projectModules)) {
            return project.projectModules.map { MPSProjectModuleAsNode(project, it) }
        }
        if (link.conformsTo(BuiltinLanguages.MPSRepositoryConcepts.Project.modules)) {
            return emptyList() // modules child link is deprecated
        }
        throw IllegalArgumentException("Unknown link $link")
    }

    override fun getPropertyValue(property: IProperty): String? {
        return if (property.conformsTo(BuiltinLanguages.jetbrains_mps_lang_core.INamedConcept.name)) {
            project.name
        } else {
            null
        }
    }

    override fun getContainmentLink(): IChildLink {
        return BuiltinLanguages.MPSRepositoryConcepts.Repository.projects
    }

    override fun getArea(): IArea {
        return MPSArea(project.repository)
    }
}
