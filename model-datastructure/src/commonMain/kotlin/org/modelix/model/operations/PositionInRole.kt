package org.modelix.model.operations

import org.modelix.model.api.IChildLinkReference

data class PositionInRole(val roleInNode: RoleInNode, val index: Int) {
    constructor(nodeId: Long, role: IChildLinkReference, index: Int) : this(RoleInNode(nodeId, role), index)
    val nodeId: Long
        get() = roleInNode.nodeId
    val role: IChildLinkReference
        get() = roleInNode.role
    override fun toString() = "$roleInNode[$index]"
    fun withIndex(newIndex: Int) = PositionInRole(roleInNode, newIndex)
}
