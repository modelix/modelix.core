package org.modelix.model.mpsadapters

import jetbrains.mps.project.ProjectBase
import org.jetbrains.mps.openapi.module.SModule
import org.jetbrains.mps.openapi.module.SRepository
import org.modelix.model.api.BuiltinLanguages
import org.modelix.model.api.IChildLinkReference
import org.modelix.model.api.IConcept
import org.modelix.model.api.INodeReference
import org.modelix.model.api.IPropertyReference
import org.modelix.model.api.IReferenceLinkReference
import org.modelix.model.api.IWritableNode
import org.modelix.mps.api.ModelixMpsApi

data class MPSProjectModuleAsNode(val project: ProjectBase, val module: SModule) : MPSGenericNodeAdapter<MPSProjectModuleAsNode>() {

    companion object {
        private val propertyAccessors: List<Pair<IPropertyReference, IPropertyAccessor<MPSProjectModuleAsNode>>> = listOf(
            BuiltinLanguages.MPSRepositoryConcepts.ProjectModule.virtualFolder.toReference() to object : IPropertyAccessor<MPSProjectModuleAsNode> {
                override fun read(element: MPSProjectModuleAsNode): String? {
                    return ModelixMpsApi.getVirtualFolder(element.project, element.module)
                }
                override fun write(element: MPSProjectModuleAsNode, value: String?) {
                    element.project.setVirtualFolder(element.module, value)
                }
            },
        )
        private val referenceAccessors: List<Pair<IReferenceLinkReference, IReferenceAccessor<MPSProjectModuleAsNode>>> = listOf(
            BuiltinLanguages.MPSRepositoryConcepts.ModuleReference.module.toReference() to object : IReferenceAccessor<MPSProjectModuleAsNode> {
                override fun read(element: MPSProjectModuleAsNode): IWritableNode? {
                    return MPSModuleAsNode(element.module)
                }

                override fun readRef(element: MPSProjectModuleAsNode): INodeReference? {
                    return read(element)?.getNodeReference()
                }

                override fun write(element: MPSProjectModuleAsNode, value: INodeReference?) {
                    throw UnsupportedOperationException("read only")
                }

                override fun write(element: MPSProjectModuleAsNode, value: IWritableNode?) {
                    throw UnsupportedOperationException("read only")
                }
            },
        )
    }

    override fun getElement(): MPSProjectModuleAsNode {
        return this
    }

    override fun getRepository(): SRepository? {
        return project.repository
    }

    override fun getPropertyAccessors(): List<Pair<IPropertyReference, IPropertyAccessor<MPSProjectModuleAsNode>>> {
        return propertyAccessors
    }

    override fun getReferenceAccessors(): List<Pair<IReferenceLinkReference, IReferenceAccessor<MPSProjectModuleAsNode>>> {
        return referenceAccessors
    }

    override fun getChildAccessors(): List<Pair<IChildLinkReference, IChildAccessor<MPSProjectModuleAsNode>>> {
        return emptyList()
    }

    override fun getParent(): MPSProjectAsNode {
        return MPSProjectAsNode(project)
    }

    override fun getNodeReference(): INodeReference {
        return MPSProjectModuleReference(
            projectRef = getParent().getNodeReference(),
            moduleRef = module.moduleReference,
        )
    }

    override fun getConcept(): IConcept {
        return BuiltinLanguages.MPSRepositoryConcepts.ProjectModule
    }

    override fun getContainmentLink(): IChildLinkReference {
        return BuiltinLanguages.MPSRepositoryConcepts.Project.projectModules.toReference()
    }
}
