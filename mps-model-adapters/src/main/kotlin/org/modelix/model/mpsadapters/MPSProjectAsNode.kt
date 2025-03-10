package org.modelix.model.mpsadapters

import com.intellij.openapi.project.ex.ProjectEx
import jetbrains.mps.ide.project.ProjectHelper
import jetbrains.mps.project.ModuleId
import jetbrains.mps.project.ProjectBase
import org.jetbrains.mps.openapi.module.SRepository
import org.jetbrains.mps.openapi.persistence.PersistenceFacade
import org.jetbrains.mps.openapi.project.Project
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
                    return element.readName()
                }

                override fun write(element: ProjectBase, value: String?) {
                    element.writeName(value)
                }
            },
        )
        private val childAccessors: List<Pair<IChildLinkReference, IChildAccessor<ProjectBase>>> = listOf(
            BuiltinLanguages.MPSRepositoryConcepts.Project.projectModules.toReference() to object : IChildAccessor<ProjectBase> {
                override fun read(element: ProjectBase): List<IWritableNode> {
                    return element.projectModules.map { MPSProjectModuleAsNode(element, it) }
                }

                override fun addNew(element: ProjectBase, index: Int, sourceNode: SpecWithResolvedConcept): IWritableNode {
                    val targetModule = requireNotNull(sourceNode.getNode().getReferenceTarget(BuiltinLanguages.MPSRepositoryConcepts.ModuleReference.module.toReference())) {
                        "Reference to module isn't set"
                    }
                    val targetName = targetModule.getPropertyValue(BuiltinLanguages.jetbrains_mps_lang_core.INamedConcept.name.toReference())
                    val targetId = requireNotNull(targetModule.getPropertyValue(BuiltinLanguages.MPSRepositoryConcepts.Module.id.toReference())) {
                        "Module ID isn't set: $targetModule"
                    }.let { ModuleId.fromString(it) }
                    val ref = PersistenceFacade.getInstance().createModuleReference(targetId, targetName)
                    val resolvedModule = checkNotNull(ref.resolve(element.repository)) { "Module not found: $ref" }
                    element.addModule(resolvedModule)
                    return MPSProjectModuleAsNode(element, resolvedModule)
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
        return MPSProjectReference(project)
    }

    override fun getConcept(): IConcept {
        return BuiltinLanguages.MPSRepositoryConcepts.Project
    }

    override fun getContainmentLink(): IChildLinkReference {
        return BuiltinLanguages.MPSRepositoryConcepts.Repository.projects.toReference()
    }
}

fun Project.readName() = ProjectHelper.toIdeaProject(this as ProjectBase).name.takeIf { it.isNotEmpty() } ?: this.name

fun Project.writeName(name: String?) {
    (ProjectHelper.toIdeaProject(this as ProjectBase) as ProjectEx).setProjectName(name ?: "")
}
