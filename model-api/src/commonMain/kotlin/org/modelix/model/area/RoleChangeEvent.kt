package org.modelix.model.area

import org.modelix.model.api.INode

abstract class RoleChangeEvent(node: INode, val role: String) : NodeChangeEvent(node)
