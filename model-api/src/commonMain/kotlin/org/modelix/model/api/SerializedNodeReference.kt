package org.modelix.model.api

@Deprecated("renamed to NodeReference", ReplaceWith("NodeReference"))
typealias SerializedNodeReference = NodeReference

data class NodeReference(val serialized: String) : INodeReference {
    override fun resolveIn(scope: INodeResolutionScope): INode? {
        val deserialized = INodeReferenceSerializer.tryDeserialize(serialized)
        if (deserialized != null) return deserialized.resolveIn(scope)
        return scope.resolveNode(this)
    }
}
