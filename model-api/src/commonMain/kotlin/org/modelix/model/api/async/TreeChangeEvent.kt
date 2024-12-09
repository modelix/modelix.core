package org.modelix.model.api.async

import org.modelix.model.api.IChildLinkReference
import org.modelix.model.api.IPropertyReference
import org.modelix.model.api.IReferenceLinkReference
import org.modelix.model.api.IRoleReference

sealed class TreeChangeEvent
sealed class NodeChangeEvent : TreeChangeEvent() {
    abstract val nodeId: Long
}
sealed class RoleChangeEvent : TreeChangeEvent() {
    abstract val nodeId: Long
    abstract val role: IRoleReference
}
data class ContainmentChangedEvent(override val nodeId: Long) : NodeChangeEvent()
data class ConceptChangedEvent(override val nodeId: Long) : NodeChangeEvent()

data class ChildrenChangedEvent(override val nodeId: Long, override val role: IChildLinkReference) : RoleChangeEvent()
data class ReferenceChangedEvent(override val nodeId: Long, override val role: IReferenceLinkReference) : RoleChangeEvent()
data class PropertyChangedEvent(override val nodeId: Long, override val role: IPropertyReference) : RoleChangeEvent()
data class NodeRemovedEvent(override val nodeId: Long) : NodeChangeEvent()
data class NodeAddedEvent(override val nodeId: Long) : NodeChangeEvent()
