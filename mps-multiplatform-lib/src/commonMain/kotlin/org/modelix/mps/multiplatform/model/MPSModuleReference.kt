package org.modelix.mps.multiplatform.model

import org.modelix.model.api.INodeReference

data class MPSModuleReference(val moduleId: String) : INodeReference() {

    companion object {
        const val PREFIX = "mps-module"
        internal const val PREFIX_COLON = "$PREFIX:"

        fun tryConvert(ref: INodeReference): MPSModuleReference? {
            if (ref is MPSModuleReference) return ref
            val serialized = ref.serialize()
            if (!serialized.startsWith(PREFIX_COLON)) return null
            val withoutPrefix = serialized.substringAfter(PREFIX_COLON)
            return parseSModuleReference(withoutPrefix)
        }

        fun parseSModuleReference(serialized: String): MPSModuleReference {
            val moduleId = if (serialized.contains('(') && serialized.contains(')')) {
                serialized.substringBefore('(')
            } else {
                serialized
            }
            return MPSModuleReference(moduleId.trim())
        }
    }

    override fun serialize(): String {
        return "$PREFIX:${toMPSString()}"
    }

    fun toMPSString() = moduleId
}
