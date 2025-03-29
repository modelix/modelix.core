package org.modelix.model.mpsadapters

import jetbrains.mps.smodel.SNodePointer
import jetbrains.mps.util.StringUtil
import org.jetbrains.mps.openapi.model.SModelReference
import org.jetbrains.mps.openapi.model.SNodeId
import org.jetbrains.mps.openapi.model.SNodeReference
import org.jetbrains.mps.openapi.persistence.PersistenceFacade
import org.jetbrains.mps.openapi.persistence.PersistenceFacade.IncorrectModelReferenceFormatException
import org.jetbrains.mps.openapi.persistence.PersistenceFacade.IncorrectNodeIdFormatException

/**
 * Implementations in MPS expect a model name to be present in the reference.
 */
object MPSReferenceParser {
    fun parseSModelReference(serialized: String): SModelReference {
        val modelRefParts = parseReferenceInternal(serialized)
        val pf = PersistenceFacade.getInstance()
        val moduleId = modelRefParts[0]?.takeIf { it.isNotBlank() }?.let { pf.createModuleId(it) }
        val modelId = modelRefParts[1]?.takeIf { it.isNotBlank() }?.let { pf.createModelId(it) }
        val moduleName = modelRefParts[2]?.takeIf { it.isNotBlank() }
        val modelName = modelRefParts[3]?.takeIf { it.isNotBlank() }
        val moduleReference = moduleId?.let { pf.createModuleReference(it, moduleName) }
        val modelReference = modelId?.let { pf.createModelReference(moduleReference, it, modelName ?: "") }
        return requireNotNull(modelReference) { "Invalid MPS model reference: $serialized" }
    }

    fun parseSNodeReference(from: String): SNodeReference {
        val delimiterIndex: Int = from.lastIndexOf('/')
        if (delimiterIndex < 0) {
            throw IncorrectNodeIdFormatException("No delimiter discovered in the passed argument " + from)
        }
        val nodeId = StringUtil.unescapeRefChars(from.substring(delimiterIndex + 1))
        val sNodeId: SNodeId? = if (nodeId == "null") {
            null
        } else {
            PersistenceFacade.getInstance().createNodeId(nodeId)
        }

        val modelReference = from.substring(0, delimiterIndex)
        val sModelReference: SModelReference? = if (modelReference == "null") {
            null
        } else {
            parseSModelReference(modelReference)
        }

        return SNodePointer(sModelReference, sNodeId)
    }

    /**
     * Format: `[ moduleID / ] modelID [ ([moduleName /] modelName ) ]`
     * @return null or 4-element array, with [module id, model id, moduleName, modelName] elements, all optional
     */
    fun parseReferenceInternal(s: String): Array<String?> {
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
            throw IncorrectModelReferenceFormatException("parentheses do not match in: `" + s + "'")
        }

        var moduleId: String? = null
        var slash = s.indexOf('/')
        if (slash >= 0) {
            moduleId = StringUtil.unescapeRefChars(s.substring(0, slash))
            s = s.substring(slash + 1)
        }

        val modelID = StringUtil.unescapeRefChars(s)

        var moduleName: String? = null
        var modelName: String? = null
        if (presentationPart != null) {
            slash = presentationPart.indexOf('/')
            if (slash >= 0) {
                moduleName = StringUtil.unescapeRefChars(presentationPart.substring(0, slash))
                modelName = StringUtil.unescapeRefChars(presentationPart.substring(slash + 1))
            } else {
                modelName = StringUtil.unescapeRefChars(presentationPart)
            }
        }
        return arrayOf<String?>(moduleId, modelID, moduleName, modelName)
    }
}
