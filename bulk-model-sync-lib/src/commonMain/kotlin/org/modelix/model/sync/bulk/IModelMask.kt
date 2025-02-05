package org.modelix.model.sync.bulk

import org.modelix.model.api.IChildLinkReference
import org.modelix.model.api.IReadableNode

interface IModelMask {
    fun <T : IReadableNode> filterChildren(parent: IReadableNode, role: IChildLinkReference, children: List<T>): List<T>
}

class UnfilteredModelMask : IModelMask {
    override fun <T : IReadableNode> filterChildren(
        parent: IReadableNode,
        role: IChildLinkReference,
        children: List<T>,
    ): List<T> {
        return children
    }
}

class CombinedModelMask(val mask1: IModelMask, val mask2: IModelMask) : IModelMask {
    override fun <T : IReadableNode> filterChildren(
        parent: IReadableNode,
        role: IChildLinkReference,
        children: List<T>,
    ): List<T> {
        return mask2.filterChildren(parent, role, mask1.filterChildren(parent, role, children))
    }
}

fun IModelMask.combine(mask2: IModelMask): IModelMask = CombinedModelMask(this, mask2)
