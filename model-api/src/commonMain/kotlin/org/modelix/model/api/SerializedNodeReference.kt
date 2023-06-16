package org.modelix.model.api

import kotlinx.serialization.Serializable

@Serializable
data class SerializedNodeReference(val serialized: String) : INodeReference {
    override fun resolveIn(scope: INodeResolutionScope): INode? {
        return INodeReferenceSerializer.deserialize(serialized).resolveIn(scope)
    }
}
