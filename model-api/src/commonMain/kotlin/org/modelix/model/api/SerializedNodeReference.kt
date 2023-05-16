package org.modelix.model.api

import kotlinx.serialization.Serializable
import org.modelix.model.area.IArea

@Serializable
data class SerializedNodeReference(val serialized: String) : INodeReference {
    override fun resolveNode(area: IArea?): INode? {
        return INodeReferenceSerializer.deserialize(serialized).resolveNode(area)
    }
}
