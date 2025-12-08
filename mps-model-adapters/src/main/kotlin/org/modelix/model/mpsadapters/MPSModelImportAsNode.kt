package org.modelix.model.mpsadapters

import org.jetbrains.mps.openapi.model.SModel
import org.jetbrains.mps.openapi.model.SModelReference
import org.modelix.model.api.BuiltinLanguages
import org.modelix.model.api.IChildLinkReference
import org.modelix.model.api.IConcept
import org.modelix.model.api.INodeReference
import org.modelix.model.api.IPropertyReference
import org.modelix.model.api.IReferenceLinkReference
import org.modelix.model.api.IWritableNode
import org.modelix.mps.multiplatform.model.MPSModelImportReference

data class MPSModelImportAsNode(val importedModel: SModelReference, val importingModel: SModel) : MPSGenericNodeAdapter<MPSModelImportAsNode>() {

    companion object {
        private val referenceAccessors = listOf(
            BuiltinLanguages.MPSRepositoryConcepts.ModelReference.model.toReference() to object :
                IReferenceAccessor<MPSModelImportAsNode> {
                override fun read(element: MPSModelImportAsNode): IWritableNode? {
                    // Broken references are a common thing and MPS also just returns null.
                    // readRef can be used to distinguish a broken reference from one that isn't set.
                    return element.importedModel.resolve(element.importingModel.repository)
                        ?.let { MPSModelAsNode(it) }
                }

                override fun readRef(element: MPSModelImportAsNode): INodeReference? {
                    return element.importedModel.toModelix()
                }

                override fun write(element: MPSModelImportAsNode, value: IWritableNode?) {
                    throw UnsupportedOperationException("read only")
                }

                override fun write(element: MPSModelImportAsNode, value: INodeReference?) {
                    throw UnsupportedOperationException("read only")
                }
            },
        )
    }

    override fun getElement(): MPSModelImportAsNode {
        return this
    }

    override fun getPropertyAccessors(): List<Pair<IPropertyReference, IPropertyAccessor<MPSModelImportAsNode>>> {
        return emptyList()
    }

    override fun getReferenceAccessors(): List<Pair<IReferenceLinkReference, IReferenceAccessor<MPSModelImportAsNode>>> {
        return referenceAccessors
    }

    override fun getChildAccessors(): List<Pair<IChildLinkReference, IChildAccessor<MPSModelImportAsNode>>> {
        return emptyList()
    }

    override fun getParent(): IWritableNode? {
        return MPSModelAsNode(importingModel)
    }

    override fun getNodeReference(): INodeReference {
        return MPSModelImportReference(
            importedModel = importedModel.toModelix(),
            importingModel = importingModel.reference.toModelix(),
        )
    }

    override fun getConcept(): IConcept {
        return BuiltinLanguages.MPSRepositoryConcepts.ModelReference
    }

    override fun getContainmentLink(): IChildLinkReference {
        return BuiltinLanguages.MPSRepositoryConcepts.Model.modelImports.toReference()
    }
}
