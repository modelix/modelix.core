package org.modelix.model.api

import org.modelix.model.area.IArea

data class PNodeReference(val id: Long, val treeId: String) : INodeReference {
    @Deprecated("Renamed to treeId", ReplaceWith("treeId"))
    val branchId: String get() = treeId

    override fun resolveNode(area: IArea?): INode? {
        return area?.resolveNode(this)
    }

    fun toLocal() = LocalPNodeReference(id)

    override fun serialize(): String {
        return "modelix:$branchId/${id.toString(16)}"
    }

    override fun toString(): String {
        return serialize()
    }

    companion object {
        fun deserialize(serialized: String): PNodeReference {
            return requireNotNull(tryDeserialize(serialized)) {
                "Not a valid PNodeReference: $serialized"
            }
        }
        fun tryDeserialize(serialized: String): PNodeReference? {
            // New format   : modelix:25038f9e-e8ad-470a-9ae8-6978ed172184/1a5003b818f
            // Legacy format: pnode:1a5003b818f@25038f9e-e8ad-470a-9ae8-6978ed172184
            //
            // The 'modelix' prefix is more intuitive for a node stored inside a Modelix repository.
            // Having the repository ID first also feels more natural.

            if (serialized.startsWith("pnode:") && serialized.contains('@')) {
                // legacy format
                val withoutPrefix = serialized.substringAfter("pnode:")
                val parts = withoutPrefix.split('@', limit = 2)
                if (parts.size != 2) return null
                val nodeId = parts[0].toLongOrNull(16) ?: return null
                return PNodeReference(nodeId, parts[1])
            } else if (serialized.startsWith("modelix:") && serialized.contains('/')) {
                val withoutPrefix = serialized.substringAfter("modelix:")
                val nodeIdStr = withoutPrefix.substringAfterLast('/')
                val branchId = withoutPrefix.substringBeforeLast('/')
                val nodeId = nodeIdStr.toLongOrNull(16)
                if (nodeId != null && branchId.isNotEmpty()) {
                    return PNodeReference(nodeId, branchId)
                }
            }
            return null
        }

        fun tryConvert(ref: INodeReference): PNodeReference? {
            if (ref is PNodeReference) return ref
            return tryDeserialize(ref.serialize())
        }
    }
}
