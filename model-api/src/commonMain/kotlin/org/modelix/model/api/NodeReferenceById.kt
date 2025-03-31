package org.modelix.model.api

import org.modelix.model.area.IArea

data class NodeReferenceById(val nodeId: String) : INodeReference() {
    override fun resolveNode(area: IArea?): INode? {
        return area?.resolveNode(this)
    }

    override fun serialize(): String {
        TODO("Not yet implemented")
    }
}
