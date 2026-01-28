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
import org.modelix.mps.multiplatform.model.MPSProjectReference

data class MPSProjectAsNode(private val id: MPSProjectReference, val project: IMPSProject) : MPSGenericNodeAdapter<MPSProjectAsNode>() {

    constructor(project: IMPSProject) : this(MPSProjectReference(project), project)

    companion object {
        private val CONTEXT_PROJECTS = ContextValue<List<MPSProjectAsNode>>(emptyList())

        fun getContextProjectNode(): MPSProjectAsNode {
            return CONTEXT_PROJECTS.getValueOrNull()?.lastOrNull() ?: MPSProjectAsNode(ModelixMpsApi.getMPSProject())
        }

        fun getContextProject(): IMPSProject = getContextProjectNode().project

        fun getContextProjectNodes(): List<MPSProjectAsNode> {
            return CONTEXT_PROJECTS.getValueOrNull()?.takeIf { it.isNotEmpty() }
                ?: ModelixMpsApi.getMPSProjects().map { MPSProjectAsNode(MPSProjectAdapter(it)) }
        }

        fun getContextProjects(): List<IMPSProject> = getContextProjectNodes().map { it.project }

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

        fun <R> runWithProjectNode(project: MPSProjectAsNode, body: () -> R): R {
            return runWithProjectNodes(listOf(project), body)
        }

        fun <R> runWithProjectNodes(projects: List<MPSProjectAsNode>, body: () -> R): R {
            if (projects.isEmpty()) return body()
            val newProjects = CONTEXT_PROJECTS.getValueOrNull().orEmpty() + projects
            return CONTEXT_PROJECTS.computeWith(newProjects) {
                ModelixMpsApi.runWithRepository(projects.last().project.getRepository(), body)
            }
        }

        fun <R> runWithProject(project: IMPSProject, body: () -> R): R {
            return runWithProjects(listOf(project), body)
        }

        fun <R> runWithProjects(projects: List<IMPSProject>, body: () -> R): R {
            if (projects.isEmpty()) return body()
            return runWithProjectNodes(projects.map { MPSProjectAsNode(it) }, body)
        }

        private val propertyAccessors: List<Pair<IPropertyReference, IPropertyAccessor<MPSProjectAsNode>>> = listOf(
            BuiltinLanguages.jetbrains_mps_lang_core.INamedConcept.name.toReference() to object : IPropertyAccessor<MPSProjectAsNode> {
                override fun read(element: MPSProjectAsNode): String? {
                    return element.project.getName()
                }

                override fun write(element: MPSProjectAsNode, value: String?) {
                    element.project.setName(value ?: "")
                }
            },
        )
        private val childAccessors: List<Pair<IChildLinkReference, IChildAccessor<MPSProjectAsNode>>> = listOf(
            BuiltinLanguages.MPSRepositoryConcepts.Project.projectModules.toReference() to object : IChildAccessor<MPSProjectAsNode> {
                override fun read(element: MPSProjectAsNode): List<IWritableNode> {
                    return element.project.getModules().map { MPSProjectModuleAsNode(element, it) }
                }

                override fun addNew(element: MPSProjectAsNode, index: Int, sourceNode: SpecWithResolvedConcept): IWritableNode {
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
                    val resolvedModule = checkNotNull(ref.resolve(element.project.getRepository())) { "Module not found: $ref" }
                    element.project.addModule(resolvedModule)
                    return MPSProjectModuleAsNode(element, resolvedModule)
                }

                override fun remove(element: MPSProjectAsNode, child: IWritableNode) {
                    element.project.removeModule((child as MPSProjectModuleAsNode).module)
                }
            },
            BuiltinLanguages.MPSRepositoryConcepts.Project.modules.toReference() to object : IChildAccessor<MPSProjectAsNode> {
                override fun read(element: MPSProjectAsNode): List<IWritableNode> {
                    return return emptyList() // modules child link is deprecated
                }

                override fun addNew(element: MPSProjectAsNode, index: Int, sourceNode: SpecWithResolvedConcept): IWritableNode {
                    throw UnsupportedOperationException("read only")
                }

                override fun remove(element: MPSProjectAsNode, child: IWritableNode) {
                    throw UnsupportedOperationException("read only")
                }
            },
        )
    }

    constructor(mpsProject: org.jetbrains.mps.openapi.project.Project) : this(MPSProjectAdapter(mpsProject))

    override fun getElement(): MPSProjectAsNode {
        return this
    }

    override fun getPropertyAccessors(): List<Pair<IPropertyReference, IPropertyAccessor<MPSProjectAsNode>>> {
        return propertyAccessors
    }

    override fun getReferenceAccessors(): List<Pair<IReferenceLinkReference, IReferenceAccessor<MPSProjectAsNode>>> {
        return emptyList()
    }

    override fun getChildAccessors(): List<Pair<IChildLinkReference, IChildAccessor<MPSProjectAsNode>>> {
        return childAccessors
    }

    override fun getParent(): IWritableNode? {
        return MPSRepositoryAsNode(project.getRepository())
    }

    override fun getNodeReference(): MPSProjectReference {
        return id
    }

    override fun getConcept(): IConcept {
        return BuiltinLanguages.MPSRepositoryConcepts.Project
    }

    override fun getContainmentLink(): IChildLinkReference {
        return BuiltinLanguages.MPSRepositoryConcepts.Repository.projects.toReference()
    }
}
