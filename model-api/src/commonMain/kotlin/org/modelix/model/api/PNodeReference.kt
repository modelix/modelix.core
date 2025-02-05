package org.modelix.model.api

import org.modelix.model.area.IArea

data class PNodeReference(val id: Long, val branchId: String) : INodeReference {
    override fun resolveNode(area: IArea?): INode? {
        return area?.resolveNode(this)
    }

    fun toLocal() = LocalPNodeReference(id)

    override fun serialize(): String {
        return "pnode:${id.toString(16)}@$branchId"
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
            if (serialized.startsWith("pnode:") && serialized.contains('@')) {
                val withoutPrefix = serialized.substringAfter("pnode:")
                val parts = withoutPrefix.split('@', limit = 2)
                if (parts.size != 2) return null
                val nodeId = parts[0].toLongOrNull(16) ?: return null
                return PNodeReference(nodeId, parts[1])
            } else if (serialized.startsWith("modelix:") && serialized.contains('/')) {
                // This would be a nicer serialization format that isn't used yet, but supporting it already will make
                // future changes easier without breaking old versions of this library.
                //
                // Example:    modelix:25038f9e-e8ad-470a-9ae8-6978ed172184/1a5003b818f
                // Old format: pnode:1a5003b818f@25038f9e-e8ad-470a-9ae8-6978ed172184
                //
                // The 'modelix' prefix is more intuitive for a node stored inside a Modelix repository.
                // Having the repository ID first also feels more natural.
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
    }
}
