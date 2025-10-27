package org.modelix.model.mpsadapters

import jetbrains.mps.project.ModuleId
import org.jetbrains.mps.openapi.persistence.PersistenceFacade
import org.modelix.kotlin.utils.ContextValue
import org.modelix.model.api.BuiltinLanguages
import org.modelix.model.api.IChildLinkReference
import org.modelix.model.api.IConcept
import org.modelix.model.api.IPropertyReference
import org.modelix.model.api.IReferenceLinkReference
import org.modelix.model.api.IWritableNode
import org.modelix.mps.api.ModelixMpsApi
import org.modelix.mps.multiplatform.model.MPSModuleReference

data class MPSProjectAsNode(val project: IMPSProject) : MPSGenericNodeAdapter<IMPSProject>() {

    companion object {
        val CONTEXT_PROJECTS = ContextValue<List<IMPSProject>>(emptyList())

        fun getContextProject(): IMPSProject {
            return CONTEXT_PROJECTS.getValueOrNull()?.lastOrNull() ?: MPSProjectAdapter(ModelixMpsApi.getMPSProject())
        }

        fun getContextProjects(): List<IMPSProject> {
            return CONTEXT_PROJECTS.getValueOrNull()?.takeIf { it.isNotEmpty() }
                ?: ModelixMpsApi.getMPSProjects().map { MPSProjectAdapter(it) }
        }

        fun getAllProjects(): List<IMPSProject> {
            return (
                ModelixMpsApi.getMPSProjects().map { MPSProjectAdapter(it) } +
                    CONTEXT_PROJECTS.getValueOrNull().orEmpty()
                ).distinct()
        }

        fun <R> runWithProject(project: org.jetbrains.mps.openapi.project.Project, body: () -> R): R {
            return runWithProjects(listOf(project), body)
        }

        @JvmName("runWithMPSProjects")
        fun <R> runWithProjects(projects: List<org.jetbrains.mps.openapi.project.Project>, body: () -> R): R {
            if (projects.isEmpty()) return body()
            return runWithProjects(projects.map { MPSProjectAdapter(it) }) {
                ModelixMpsApi.runWithProject(projects.last(), body)
            }
        }

        fun <R> runWithProject(project: IMPSProject, body: () -> R): R {
            return runWithProjects(listOf(project), body)
        }

        fun <R> runWithProjects(projects: List<IMPSProject>, body: () -> R): R {
            if (projects.isEmpty()) return body()
            return CONTEXT_PROJECTS.computeWith(CONTEXT_PROJECTS.getValueOrNull().orEmpty() + projects) {
                ModelixMpsApi.runWithRepository(projects.last().getRepository(), body)
            }
        }

        private val propertyAccessors: List<Pair<IPropertyReference, IPropertyAccessor<IMPSProject>>> = listOf(
            BuiltinLanguages.jetbrains_mps_lang_core.INamedConcept.name.toReference() to object : IPropertyAccessor<IMPSProject> {
                override fun read(element: IMPSProject): String? {
                    return element.getName()
                }

                override fun write(element: IMPSProject, value: String?) {
                    element.setName(value ?: "")
                }
            },
        )
        private val childAccessors: List<Pair<IChildLinkReference, IChildAccessor<IMPSProject>>> = listOf(
            BuiltinLanguages.MPSRepositoryConcepts.Project.projectModules.toReference() to object : IChildAccessor<IMPSProject> {
                override fun read(element: IMPSProject): List<IWritableNode> {
                    return element.getModules().map { MPSProjectModuleAsNode(element, it) }
                }

                override fun addNew(element: IMPSProject, index: Int, sourceNode: SpecWithResolvedConcept): IWritableNode {
                    val targetModuleRef = requireNotNull(sourceNode.getNode().getReferenceTargetRef(BuiltinLanguages.MPSRepositoryConcepts.ModuleReference.module.toReference())) {
                        "Reference to module isn't set"
                    }
                    val mpsTargetModuleRef = requireNotNull(MPSModuleReference.tryConvert(targetModuleRef)) {
                        "Not an MPS module reference: $targetModuleRef"
                    }
                    val targetModule = sourceNode.getNode().getReferenceTarget(BuiltinLanguages.MPSRepositoryConcepts.ModuleReference.module.toReference())
                    val targetName = targetModule?.getPropertyValue(BuiltinLanguages.jetbrains_mps_lang_core.INamedConcept.name.toReference())
                    val targetId = (
                        targetModule?.getPropertyValue(BuiltinLanguages.MPSRepositoryConcepts.Module.id.toReference())
                            ?: mpsTargetModuleRef.moduleId
                        ).let { ModuleId.fromString(it) }
                    val ref = PersistenceFacade.getInstance().createModuleReference(targetId, targetName)
                    val resolvedModule = checkNotNull(ref.resolve(element.getRepository())) { "Module not found: $ref" }
                    element.addModule(resolvedModule)
                    return MPSProjectModuleAsNode(element, resolvedModule)
                }

                override fun remove(element: IMPSProject, child: IWritableNode) {
                    element.removeModule((child as MPSProjectModuleAsNode).module)
                }
            },
            BuiltinLanguages.MPSRepositoryConcepts.Project.modules.toReference() to object : IChildAccessor<IMPSProject> {
                override fun read(element: IMPSProject): List<IWritableNode> {
                    return return emptyList() // modules child link is deprecated
                }

                override fun addNew(element: IMPSProject, index: Int, sourceNode: SpecWithResolvedConcept): IWritableNode {
                    throw UnsupportedOperationException("read only")
                }

                override fun remove(element: IMPSProject, child: IWritableNode) {
                    throw UnsupportedOperationException("read only")
                }
            },
        )
    }

    constructor(mpsProject: org.jetbrains.mps.openapi.project.Project) : this(MPSProjectAdapter(mpsProject))

    override fun getElement(): IMPSProject {
        return project
    }

    override fun getPropertyAccessors(): List<Pair<IPropertyReference, IPropertyAccessor<IMPSProject>>> {
        return propertyAccessors
    }

    override fun getReferenceAccessors(): List<Pair<IReferenceLinkReference, IReferenceAccessor<IMPSProject>>> {
        return emptyList()
    }

    override fun getChildAccessors(): List<Pair<IChildLinkReference, IChildAccessor<IMPSProject>>> {
        return childAccessors
    }

    override fun getParent(): IWritableNode? {
        return MPSRepositoryAsNode(project.getRepository())
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
