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

                override fun write(element: ProjectBase, value: String?) {
                    throw UnsupportedOperationException("read only")
                }
            },
        )
        private val childAccessors: List<Pair<IChildLinkReference, IChildAccessor<ProjectBase>>> = listOf(
            BuiltinLanguages.MPSRepositoryConcepts.Project.projectModules.toReference() to object : IChildAccessor<ProjectBase> {
                override fun read(element: ProjectBase): List<IWritableNode> {
                    return element.projectModules.map { MPSProjectModuleAsNode(element, it) }
                }

                override fun addNew(element: ProjectBase, index: Int, sourceNode: SpecWithResolvedConcept): IWritableNode {
                    return TODO()
                }

                override fun remove(element: ProjectBase, child: IWritableNode) {
                    element.removeModule((child as MPSProjectModuleAsNode).module)
                }
            },
            BuiltinLanguages.MPSRepositoryConcepts.Project.modules.toReference() to object : IChildAccessor<ProjectBase> {
                override fun read(element: ProjectBase): List<IWritableNode> {
                    return return emptyList() // modules child link is deprecated
                }

                override fun addNew(element: ProjectBase, index: Int, sourceNode: SpecWithResolvedConcept): IWritableNode {
                    throw UnsupportedOperationException("read only")
                }

                override fun remove(element: ProjectBase, child: IWritableNode) {
                    throw UnsupportedOperationException("read only")
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
