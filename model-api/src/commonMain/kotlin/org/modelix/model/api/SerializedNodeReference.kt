package org.modelix.model.api

import kotlinx.serialization.Serializable

@Serializable
data class SerializedNodeReference(val serialized: String) : INodeReference {
    override fun resolveIn(scope: INodeResolutionScope): INode? {
        val deserialized = INodeReferenceSerializer.tryDeserialize(serialized)
        if (deserialized != null) return deserialized.resolveIn(scope)
        return scope.resolveNode(this)
    }
}
