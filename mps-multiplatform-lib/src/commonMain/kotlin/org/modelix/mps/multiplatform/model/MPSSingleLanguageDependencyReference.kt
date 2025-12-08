package org.modelix.mps.multiplatform.model

import org.modelix.model.api.INodeReference
import org.modelix.model.api.NodeReference
import org.modelix.model.api.NodeReferenceConverter

data class MPSSingleLanguageDependencyReference(
    val usedModuleId: MPSModuleReference,
    val userModule: MPSModuleReference? = null,
    val userModel: MPSModelReference? = null,
) : INodeReference() {

    companion object : NodeReferenceConverter<MPSSingleLanguageDependencyReference> {
        const val PREFIX = "mps-lang"
        const val PREFIX_COLON = "$PREFIX:"
        const val SEPARATOR = "#IN#"

        override fun tryConvert(ref: INodeReference): MPSSingleLanguageDependencyReference? {
            if (ref is MPSSingleLanguageDependencyReference) return ref

            val serialized = ref.serialize()
            val target = serialized.substringAfter(PREFIX_COLON).substringBefore(SEPARATOR)
            val source = serialized.substringAfter(SEPARATOR).let { NodeReference(it) }

            return MPSSingleLanguageDependencyReference(
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
