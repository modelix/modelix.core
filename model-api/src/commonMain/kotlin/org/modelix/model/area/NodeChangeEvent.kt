package org.modelix.model.area

import org.modelix.model.api.INode

abstract class NodeChangeEvent(val node: INode) : IAreaChangeEvent {
    abstract fun withNode(substitute: INode): NodeChangeEvent
}
