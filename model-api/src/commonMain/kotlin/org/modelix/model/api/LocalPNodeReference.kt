package org.modelix.model.api

import org.modelix.model.area.IArea

data class LocalPNodeReference(val id: Long) : INodeReference() {

    override fun resolveNode(area: IArea?): INode? {
        throw UnsupportedOperationException("Use .toGlobal first to specify a branch")
    }

    fun toGlobal(branchId: String) = PNodeReference(id, branchId)

    override fun serialize(): String {
        return id.toULong().toString(16)
    }

    override fun toString(): String {
        return "LocalPNodeReference_${id.toString(16)}"
    }
}
