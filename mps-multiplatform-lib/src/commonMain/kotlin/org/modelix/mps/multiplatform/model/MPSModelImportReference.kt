package org.modelix.mps.multiplatform.model

import org.modelix.model.api.INodeReference
import org.modelix.model.api.NodeReferenceConverter

data class MPSModelImportReference(
    val importedModel: MPSModelReference,
    val importingModel: MPSModelReference,
) : INodeReference() {

    companion object : NodeReferenceConverter<MPSModelImportReference> {
        const val PREFIX = "mps-model-import"
        const val PREFIX_COLON = "$PREFIX:"
        const val SEPARATOR = "#IN#"

        override fun tryConvert(ref: INodeReference): MPSModelImportReference? {
            if (ref is MPSModelImportReference) return ref

            val serialized = ref.serialize()
            if (!serialized.startsWith(PREFIX_COLON)) return null

            val serializedTarget = serialized.substringAfter(PREFIX_COLON).substringBefore(SEPARATOR)
            val serializedSource = serialized.substringAfter(SEPARATOR)
            val source = MPSModelReference.parseSModelReference(serializedSource)
            val target = MPSModelReference.parseSModelReference(serializedTarget)

            return MPSModelImportReference(target, source)
        }
    }

    override fun serialize(): String {
        return "$PREFIX:${importedModel.toMPSString()}$SEPARATOR${importingModel.toMPSString()}"
    }
}
