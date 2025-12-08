package org.modelix.mps.multiplatform.model

import org.modelix.model.api.INodeReference
import org.modelix.model.api.NodeReferenceConverter

data class MPSProjectReference(val projectName: String) : INodeReference() {

    companion object : NodeReferenceConverter<MPSProjectReference> {
        const val PREFIX = "mps-project"
        const val PREFIX_COLON = "$PREFIX:"

        override fun tryConvert(ref: INodeReference): MPSProjectReference? {
            if (ref is MPSProjectReference) return ref
            val serialized = ref.serialize()
            return if (serialized.startsWith(PREFIX_COLON)) {
                MPSProjectReference(serialized.substringAfter(PREFIX_COLON))
            } else {
                null
            }
        }
    }

    override fun serialize(): String {
        return "$PREFIX:$projectName"
    }
}
