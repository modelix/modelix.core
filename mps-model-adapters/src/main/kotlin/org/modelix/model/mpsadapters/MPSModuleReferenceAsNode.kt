package org.modelix.model.mpsadapters

import jetbrains.mps.smodel.MPSModuleRepository
import org.jetbrains.mps.openapi.module.SModuleId
import org.jetbrains.mps.openapi.module.SModuleReference
import org.jetbrains.mps.openapi.module.SRepository
import org.modelix.model.api.BuiltinLanguages
import org.modelix.model.api.ChildLinkReferenceByUID
import org.modelix.model.api.IChildLinkReference
import org.modelix.model.api.IConcept
import org.modelix.model.api.INodeReference
import org.modelix.model.api.IPropertyReference
import org.modelix.model.api.IReferenceLinkReference
import org.modelix.model.api.IWritableNode

data class MPSModuleReferenceAsNode(
    private val parent: MPSModuleAsNode<*>,
    private val containmentLink: IChildLinkReference,
    val target: SModuleReference,
) : MPSGenericNodeAdapter<MPSModuleReferenceAsNode>() {
    override fun getElement(): MPSModuleReferenceAsNode {
        return this
    }

    override fun getRepository(): SRepository? {
        return parent.getRepository()
    }

    override fun getPropertyAccessors(): List<Pair<IPropertyReference, IPropertyAccessor<MPSModuleReferenceAsNode>>> {
        return emptyList()
    }

    override fun getReferenceAccessors(): List<Pair<IReferenceLinkReference, IReferenceAccessor<MPSModuleReferenceAsNode>>> {
        return listOf(
            BuiltinLanguages.MPSRepositoryConcepts.ModuleReference.module.toReference() to object : IReferenceAccessor<MPSModuleReferenceAsNode> {
                override fun read(element: MPSModuleReferenceAsNode): IWritableNode? {
                    val repo = parent.getRepository() ?: MPSModuleRepository.getInstance()
                    return target.resolve(repo)?.let { MPSModuleAsNode(it) }
                }
            },
        )
    }

    override fun getChildAccessors(): List<Pair<IChildLinkReference, IChildAccessor<MPSModuleReferenceAsNode>>> {
        return emptyList()
    }

    override fun getParent(): IWritableNode? {
        return parent
    }

    override fun getNodeReference(): INodeReference {
        return MPSModuleReferenceReference(parent.module.moduleId, ChildLinkReferenceByUID(containmentLink.getUID()!!), target.moduleId)
    }

    override fun getConcept(): IConcept {
        return BuiltinLanguages.MPSRepositoryConcepts.ModuleReference
    }

    override fun getContainmentLink(): IChildLinkReference {
        return containmentLink
    }
}

data class MPSModuleReferenceReference(val parent: SModuleId, val link: ChildLinkReferenceByUID, val target: SModuleId) : INodeReference {
    companion object {
        internal const val PREFIX = "mps-module-ref"
        internal const val SEPARATOR = "#"
    }

    override fun serialize(): String {
        return "$PREFIX:$parent$SEPARATOR${link.getUID()}$SEPARATOR$target"
    }
}
