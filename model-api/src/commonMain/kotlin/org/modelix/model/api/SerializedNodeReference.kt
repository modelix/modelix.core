package org.modelix.model.api

@Deprecated("renamed to NodeReference", ReplaceWith("NodeReference"))
typealias SerializedNodeReference = NodeReference

data class NodeReference(val serialized: String) : INodeReference
