package org.modelix.mps.multiplatform.model

import org.modelix.model.api.INodeReference
import org.modelix.model.api.NodeReferenceConverter
import org.modelix.model.randomUUID

data class MPSModelReference(val moduleReference: MPSModuleReference?, val modelId: String) : INodeReference() {

    companion object : NodeReferenceConverter<MPSModelReference> {
        const val PREFIX = "mps-model"

        override fun tryConvert(ref: INodeReference): MPSModelReference? {
            if (ref is MPSModelReference) return ref
            val serialized = ref.serialize()
            val serializedMPSRef = when {
                serialized.startsWith("mps-model:") -> serialized.substringAfter("mps-model:")
                else -> return null
            }
            return parseSModelReference(serializedMPSRef)
        }

        fun parseSModelReference(serialized: String): MPSModelReference {
            val modelRefParts = parseReferenceInternal(serialized)
            val moduleId = modelRefParts[0]?.takeIf { it.isNotBlank() }
            val modelId = modelRefParts[1]?.takeIf { it.isNotBlank() }
            val moduleName = modelRefParts[2]?.takeIf { it.isNotBlank() }
            val modelName = modelRefParts[3]?.takeIf { it.isNotBlank() }
            val moduleReference = moduleId?.let { MPSModuleReference(it) }
            val modelReference = modelId?.let { MPSModelReference(moduleReference, it) }
            return requireNotNull(modelReference) { "Invalid MPS model reference: $serialized" }
        }

        /**
         * Format: `[ moduleID / ] modelID [ ([moduleName /] modelName ) ]`
         * @return null or 4-element array, with [module id, model id, moduleName, modelName] elements, all optional
         */
        private fun parseReferenceInternal(s: String): Array<String?> {
            var s = s
            s = s.trim { it <= ' ' }
            var lParen = s.indexOf('(')
            var rParen = s.lastIndexOf(')')
            var presentationPart: String? = null
            if (lParen > 0 && rParen == s.length - 1) {
                presentationPart = s.substring(lParen + 1, rParen)
                s = s.substring(0, lParen)
                lParen = s.indexOf('(')
                rParen = s.lastIndexOf(')')
            }
            if (lParen != -1 || rParen != -1) {
                throw IllegalArgumentException("parentheses do not match in: $s")
            }

            var moduleId: String? = null
            var slash = s.indexOf('/')
            if (slash >= 0) {
                moduleId = unescapeRefChars(s.substring(0, slash))
                s = s.substring(slash + 1)
            }

            val modelID = unescapeRefChars(s)

            var moduleName: String? = null
            var modelName: String? = null
            if (presentationPart != null) {
                slash = presentationPart.indexOf('/')
                if (slash >= 0) {
                    moduleName = unescapeRefChars(presentationPart.substring(0, slash))
                    modelName = unescapeRefChars(presentationPart.substring(slash + 1))
                } else {
                    modelName = unescapeRefChars(presentationPart)
                }
            }
            return arrayOf<String?>(moduleId, modelID, moduleName, modelName)
        }

        fun random(): MPSModelReference = MPSModelReference(null, "r:" + randomUUID())
    }

    override fun serialize(): String {
        return "$PREFIX:${toMPSString()}"
    }

    fun toMPSString() = (moduleReference?.let { escapeRefChars(it.moduleId) + "/" } ?: "") + escapeRefChars(modelId)
}
