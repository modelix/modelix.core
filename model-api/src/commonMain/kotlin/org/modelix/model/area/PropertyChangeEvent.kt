package org.modelix.model.area

import org.modelix.model.api.INode

class PropertyChangeEvent(node: INode, role: String) : RoleChangeEvent(node, role) {
    override fun withNode(substitute: INode) = PropertyChangeEvent(substitute, role)
}
