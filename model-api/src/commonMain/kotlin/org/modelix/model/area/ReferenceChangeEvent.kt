package org.modelix.model.area

import org.modelix.model.api.INode

class ReferenceChangeEvent(node: INode, role: String) : RoleChangeEvent(node, role) {
    override fun withNode(substitute: INode) = ReferenceChangeEvent(substitute, role)
}
