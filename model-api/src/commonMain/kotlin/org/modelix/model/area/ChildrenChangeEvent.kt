package org.modelix.model.area

import org.modelix.model.api.INode

class ChildrenChangeEvent(node: INode, role: String) : RoleChangeEvent(node, role) {
    override fun withNode(substitute: INode) = ChildrenChangeEvent(substitute, role)
}
