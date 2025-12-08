package org.modelix.mps.multiplatform.model

import org.modelix.model.api.INodeReference
import org.modelix.model.api.NodeReferenceConverter

data class MPSModuleDependencyReference(
    val usedModuleId: MPSModuleReference,
    val userModuleReference: MPSModuleReference,
) : INodeReference() {

    companion object : NodeReferenceConverter<MPSModuleDependencyReference> {
        const val PREFIX = "mps-module-dep"
        const val PREFIX_COLON = "$PREFIX:"
        const val SEPARATOR = "#IN#"

        override fun tryConvert(ref: INodeReference): MPSModuleDependencyReference? {
            if (ref is MPSModuleDependencyReference) return ref

            val serialized = ref.serialize()
            if (!serialized.startsWith(PREFIX_COLON)) return null

            val serializedTargetId = serialized.substringAfter(PREFIX_COLON).substringBefore(SEPARATOR)
            val serializedSourceId = serialized.substringAfter(SEPARATOR)

            return MPSModuleDependencyReference(
                MPSModuleReference.parseSModuleReference(serializedTargetId),
                MPSModuleReference.parseSModuleReference(serializedSourceId),
            )
        }
    }

    override fun serialize(): String {
        return "$PREFIX:${usedModuleId.toMPSString()}$SEPARATOR${userModuleReference.toMPSString()}"
    }
}
