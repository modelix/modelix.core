package org.modelix.model.api

import org.modelix.model.area.IArea

class SerializedNodeReference(val serialized: String) : INodeReference {
    override fun resolveNode(area: IArea?): INode? {
        return INodeReferenceSerializer.deserialize(serialized).resolveNode(area)
    }
}
