package org.modelix.mps.multiplatform.model

import org.modelix.model.api.INodeReference
import org.modelix.model.api.NodeReference
import org.modelix.model.api.NodeReferenceConverter

data class MPSDevKitDependencyReference(
    val usedModuleId: MPSModuleReference,
    val userModule: MPSModuleReference? = null,
    val userModel: MPSModelReference? = null,
) : INodeReference() {

    companion object : NodeReferenceConverter<MPSDevKitDependencyReference> {
        const val PREFIX = "mps-devkit"
        const val PREFIX_COLON = "$PREFIX:"
        const val SEPARATOR = "#IN#"

        override fun tryConvert(ref: INodeReference): MPSDevKitDependencyReference? {
            if (ref is MPSDevKitDependencyReference) return ref

            val serialized = ref.serialize()
            val target = serialized.substringAfter(PREFIX_COLON).substringBefore(SEPARATOR)
            val source = serialized.substringAfter(SEPARATOR).let { NodeReference(it) }

            return MPSDevKitDependencyReference(
                MPSModuleReference.parseSModuleReference(target),
                MPSModuleReference.tryConvert(source),
                MPSModelReference.tryConvert(source),
            )
        }
    }

    override fun serialize(): String {
        val importer = userModule?.serialize()
            ?: userModel?.serialize()
            ?: error("importer not found")

        return "$PREFIX:${usedModuleId.toMPSString()}$SEPARATOR$importer"
    }
}
