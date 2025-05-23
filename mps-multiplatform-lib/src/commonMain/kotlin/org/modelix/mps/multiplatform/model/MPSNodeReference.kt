package org.modelix.mps.multiplatform.model

import org.modelix.model.api.INodeReference

data class MPSNodeReference(val modelReference: MPSModelReference?, val nodeId: String) : INodeReference() {
    companion object {

        const val PREFIX = "mps"

        fun tryConvert(ref: INodeReference): MPSNodeReference? {
            if (ref is MPSNodeReference) return ref
            val serialized = ref.serialize()
            val serializedMPSRef = when {
                serialized.startsWith("mps-node:") -> serialized.substringAfter("mps-node:")
                serialized.startsWith("mps:") -> serialized.substringAfter("mps:")
                else -> return null
            }
            return parseSNodeReference(serializedMPSRef)
        }

        fun parseSNodeReference(from: String): MPSNodeReference {
            val delimiterIndex: Int = from.lastIndexOf('/')
            if (delimiterIndex < 0) {
                throw IllegalArgumentException("No delimiter discovered: $from")
            }
            val nodeId = unescapeRefChars(from.substring(delimiterIndex + 1)).takeIf { it != "null" }
                ?: throw IllegalArgumentException("Node ID missing: $from")

            val modelReference = from.substring(0, delimiterIndex).takeIf { it != "null" }
                ?.let { MPSModelReference.parseSModelReference(it) }

            return MPSNodeReference(modelReference, nodeId)
        }
    }

    override fun serialize(): String {
        return "$PREFIX:${toMPSString()}"
    }

    fun toMPSString() = "${modelReference?.toMPSString()}/${escapeRefChars(nodeId)}"
}
