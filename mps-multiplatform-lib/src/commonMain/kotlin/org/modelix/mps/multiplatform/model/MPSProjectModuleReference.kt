package org.modelix.mps.multiplatform.model

import org.modelix.model.api.INodeReference
import org.modelix.model.api.NodeReference
import org.modelix.model.api.NodeReferenceConverter

data class MPSProjectModuleReference(val moduleRef: MPSModuleReference, val projectRef: MPSProjectReference) : INodeReference() {

    companion object : NodeReferenceConverter<MPSProjectModuleReference> {
        const val PREFIX = "mps-project-module"
        const val PREFIX_COLON = "$PREFIX:"
        const val SEPARATOR = "#IN#"

        override fun tryConvert(ref: INodeReference): MPSProjectModuleReference? {
            if (ref is MPSProjectModuleReference) return ref
            val serialized = ref.serialize()
            if (!serialized.startsWith(PREFIX_COLON)) return null
            val moduleRef = serialized
                .substringAfter(PREFIX_COLON)
                .substringBefore(SEPARATOR)
                .let { MPSModuleReference.parseSModuleReference(it) }
            val projectRef = NodeReference(serialized.substringAfter(SEPARATOR))
                .let { MPSProjectReference.tryConvert(it) }
                .let { requireNotNull(it) { "Invalid project reference: $it" } }
            return MPSProjectModuleReference(moduleRef, projectRef)
        }
    }

    override fun serialize(): String {
        return "$PREFIX:${moduleRef.toMPSString()}$SEPARATOR${projectRef.serialize()}"
    }
}
