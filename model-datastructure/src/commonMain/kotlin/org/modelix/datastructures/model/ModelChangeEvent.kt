package org.modelix.datastructures.model

import org.modelix.model.api.IChildLinkReference
import org.modelix.model.api.IPropertyReference
import org.modelix.model.api.IReferenceLinkReference
import org.modelix.model.api.IRoleReference

sealed class ModelChangeEvent<NodeId>
sealed class NodeChangeEvent<NodeId> : ModelChangeEvent<NodeId>() {
    abstract val nodeId: NodeId
}
sealed class RoleChangeEvent<NodeId> : NodeChangeEvent<NodeId>() {
    abstract override val nodeId: NodeId
    abstract val role: IRoleReference
}
data class ContainmentChangedEvent<NodeId>(override val nodeId: NodeId) : NodeChangeEvent<NodeId>()
data class ConceptChangedEvent<NodeId>(override val nodeId: NodeId) : NodeChangeEvent<NodeId>()

data class ChildrenChangedEvent<NodeId>(override val nodeId: NodeId, override val role: IChildLinkReference) : RoleChangeEvent<NodeId>()
data class ReferenceChangedEvent<NodeId>(override val nodeId: NodeId, override val role: IReferenceLinkReference) : RoleChangeEvent<NodeId>()
data class PropertyChangedEvent<NodeId>(override val nodeId: NodeId, override val role: IPropertyReference) : RoleChangeEvent<NodeId>()
data class NodeRemovedEvent<NodeId>(override val nodeId: NodeId) : NodeChangeEvent<NodeId>()
data class NodeAddedEvent<NodeId>(override val nodeId: NodeId) : NodeChangeEvent<NodeId>()

fun ModelChangeEvent<Long>.toLegacy() = when (this) {
    is ConceptChangedEvent<Long> -> org.modelix.model.api.async.ConceptChangedEvent(nodeId)
    is ContainmentChangedEvent<Long> -> org.modelix.model.api.async.ContainmentChangedEvent(nodeId)
    is NodeAddedEvent<Long> -> org.modelix.model.api.async.NodeAddedEvent(nodeId)
    is NodeRemovedEvent<Long> -> org.modelix.model.api.async.NodeRemovedEvent(nodeId)
    is ChildrenChangedEvent<Long> -> org.modelix.model.api.async.ChildrenChangedEvent(nodeId, role)
    is PropertyChangedEvent<Long> -> org.modelix.model.api.async.PropertyChangedEvent(nodeId, role)
    is ReferenceChangedEvent<Long> -> org.modelix.model.api.async.ReferenceChangedEvent(nodeId, role)
}
