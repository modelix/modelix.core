package org.modelix.model.mpsadapters

import org.jetbrains.mps.openapi.model.SModel
import org.modelix.model.api.BuiltinLanguages
import org.modelix.model.api.IChildLink
import org.modelix.model.api.IConcept
import org.modelix.model.api.INode
import org.modelix.model.api.INodeReference
import org.modelix.model.api.IProperty
import org.modelix.model.api.IReferenceLink
import org.modelix.model.area.IArea

data class MPSModelImportAsNode(val importedModel: SModel, val importingModel: SModel) : IDefaultNodeAdapter {
    override fun getArea(): IArea =
        MPSArea(importingModel.repository)

    override val reference: INodeReference
        get() = MPSModelImportReference(
            importedModel = importedModel.reference,
            importingModel = importingModel.reference,
        )
    override val concept: IConcept
        get() = BuiltinLanguages.MPSRepositoryConcepts.ModelReference
    override val parent: INode
        get() = MPSModelAsNode(importingModel)

    override fun getReferenceTarget(link: IReferenceLink): INode {
        require(link.conformsTo(BuiltinLanguages.MPSRepositoryConcepts.ModelReference.model)) {
            "Unknown reference link '$link'"
        }
        return MPSModelAsNode(importedModel)
    }

    override fun getReferenceTargetRef(role: IReferenceLink): INodeReference {
        return getReferenceTarget(role).reference
    }

    override fun setReferenceTarget(link: IReferenceLink, target: INode?) {
        if (link.conformsTo(BuiltinLanguages.MPSRepositoryConcepts.ModelReference.model)) {
            throw UnsupportedOperationException("ModelReference.model is read only.")
        }
    }

    override fun setReferenceTarget(role: IReferenceLink, target: INodeReference?) {
        setReferenceTarget(role, null as INode?)
    }

    override fun getPropertyValue(property: IProperty): String? {
        return reference.serialize()
    }

    override fun setPropertyValue(property: IProperty, value: String?) {
        throw UnsupportedOperationException("Concept $concept does not have properties.")
    }

    override fun getContainmentLink(): IChildLink {
        return BuiltinLanguages.MPSRepositoryConcepts.Model.modelImports
    }
}
