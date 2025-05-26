package org.modelix.model.mpsadapters

import org.modelix.model.TreeId
import org.modelix.model.api.IIdGenerator
import org.modelix.model.api.INodeReference
import org.modelix.model.api.NodeReference
import org.modelix.model.mutable.INodeIdGenerator

class MPSIdGenerator(val int64Generator: IIdGenerator, val treeId: TreeId) : INodeIdGenerator<INodeReference> {
    override fun generate(parentNode: INodeReference): INodeReference {
        // todo MPSModelReference.tryConvert still depends on mps
        val modelRef = MPSNodeReference.tryConvert(parentNode)?.ref?.modelReference
            ?: MPSModelReference.tryConvert(parentNode)?.modelReference
        require(modelRef != null) { "Cannot generate IDs for nodes in $parentNode" }

        // from SNodeId.Foreign
        val nodeId = "~${escapeRefChars(treeId.id)}-${int64Generator.generate().toULong().toString(16)}".intern();

        // from withoutNames
        val modelPrefix = modelRef?.let { it.withoutNames() + "/" } ?: ""
        val refWithoutNames = modelPrefix + escapeRefChars(nodeId ?: "")

        return NodeReference("$PREFIX:$refWithoutNames")
    }

    private val PREFIX = "mps"

    /**
     * copied from jetbrains.mps.util.StringUtil.escapeRefChars to make this class run in kotlin-multiplatform
     */
    private val STINGUTIL_HEX_DIGITS: CharArray =
        charArrayOf('0', '1', '2', '3', '4', '5', '6', '7', '8', '9', 'A', 'B', 'C', 'D', 'E', 'F')
    /**
     * copied from jetbrains.mps.util.StringUtil.escapeRefChars to make this class run in kotlin-multiplatform
     */
    private fun escapeRefChars(text: String?): String? {
        if (text != null && !text.isEmpty()) {
            val sb = StringBuilder()
            val len = text.length

            for (i in 0 until len) {
                val c = text[i]
                when (c) {
                    '%', '(', ')', '/' -> {
                        sb.append('%')
                        sb.append(STINGUTIL_HEX_DIGITS[c.code shr 4 and 15])
                        sb.append(STINGUTIL_HEX_DIGITS[c.code and 15])
                    }

                    else -> sb.append(c)
                }
            }

            return sb.toString()
        } else {
            return text
        }
    }
}
