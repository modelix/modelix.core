package org.modelix.model.mpsadapters

import jetbrains.mps.project.ProjectBase
import org.jetbrains.mps.openapi.module.SRepository
import org.modelix.model.api.BuiltinLanguages
import org.modelix.model.api.IChildLinkReference
import org.modelix.model.api.IConcept
import org.modelix.model.api.IPropertyReference
import org.modelix.model.api.IReferenceLinkReference
import org.modelix.model.api.IWritableNode

data class MPSProjectAsNode(val project: ProjectBase) : MPSGenericNodeAdapter<ProjectBase>() {

    companion object {
        private val propertyAccessors: List<Pair<IPropertyReference, IPropertyAccessor<ProjectBase>>> = listOf(
            BuiltinLanguages.jetbrains_mps_lang_core.INamedConcept.name.toReference() to object : IPropertyAccessor<ProjectBase> {
                override fun read(element: ProjectBase): String? {
                    return element.name
                }
            },
        )
        private val childAccessors: List<Pair<IChildLinkReference, IChildAccessor<ProjectBase>>> = listOf(
            BuiltinLanguages.MPSRepositoryConcepts.Project.projectModules.toReference() to object : IChildAccessor<ProjectBase> {
                override fun read(element: ProjectBase): List<IWritableNode> {
                    return element.projectModules.map { MPSProjectModuleAsNode(element, it) }
                }
            },
            BuiltinLanguages.MPSRepositoryConcepts.Project.modules.toReference() to object : IChildAccessor<ProjectBase> {
                override fun read(element: ProjectBase): List<IWritableNode> {
                    return return emptyList() // modules child link is deprecated
                }
            },
        )
    }

    override fun getElement(): ProjectBase {
        return project
    }

    override fun getRepository(): SRepository? {
        return project.repository
    }

    override fun getPropertyAccessors(): List<Pair<IPropertyReference, IPropertyAccessor<ProjectBase>>> {
        return propertyAccessors
    }

    override fun getReferenceAccessors(): List<Pair<IReferenceLinkReference, IReferenceAccessor<ProjectBase>>> {
        return emptyList()
    }

    override fun getChildAccessors(): List<Pair<IChildLinkReference, IChildAccessor<ProjectBase>>> {
        return childAccessors
    }

    override fun getParent(): IWritableNode? {
        return MPSRepositoryAsNode(project.repository)
    }

    override fun getNodeReference(): MPSProjectReference {
        return MPSProjectReference(project.name)
    }

    override fun getConcept(): IConcept {
        return BuiltinLanguages.MPSRepositoryConcepts.Project
    }

    override fun getContainmentLink(): IChildLinkReference {
        return BuiltinLanguages.MPSRepositoryConcepts.Repository.projects.toReference()
    }
}
