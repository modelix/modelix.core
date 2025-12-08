package org.modelix.mps.multiplatform.model

import org.modelix.model.api.INodeReference

data class MPSModelImportReference(
    val importedModel: MPSModelReference,
    val importingModel: MPSModelReference,
) : INodeReference() {

    companion object {
        const val PREFIX = "mps-model-import"
        const val SEPARATOR = "#IN#"
    }

    override fun serialize(): String {
        return "$PREFIX:${importedModel.toMPSString()}$SEPARATOR${importingModel.toMPSString()}"
    }
}
