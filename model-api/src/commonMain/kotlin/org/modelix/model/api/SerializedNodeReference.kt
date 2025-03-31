package org.modelix.model.api

import kotlinx.serialization.Serializable

@Deprecated("renamed to NodeReference", ReplaceWith("NodeReference"))
typealias SerializedNodeReference = NodeReference

@Serializable
data class NodeReference(val serialized: String) : INodeReference() {
    override fun serialize(): String = serialized
}
