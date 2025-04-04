package org.modelix.model.mutable

import org.modelix.model.api.IMutableModel
import org.modelix.model.api.INodeReference
import org.modelix.model.api.IWritableNode

class MutableModelTreeAsModel(val tree: IMutableModelTree) : IMutableModel {
    override fun getRootNode(): IWritableNode {
        return tree.getRootNode()
    }

    override fun getRootNodes(): List<IWritableNode> {
        return listOf(getRootNode())
    }

    override fun tryResolveNode(ref: INodeReference): IWritableNode? {
        return NodeInMutableModel(tree, ref).takeIf { it.isValid() }
    }

    override fun <R> executeRead(body: () -> R): R {
        return tree.runRead { body() }
    }

    override fun <R> executeWrite(body: () -> R): R {
        return tree.runWrite { body() }
    }

    override fun canRead(): Boolean {
        return tree.canRead()
    }

    override fun canWrite(): Boolean {
        return tree.canWrite()
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other == null || this::class != other::class) return false

        other as MutableModelTreeAsModel

        return tree == other.tree
    }

    override fun hashCode(): Int {
        return tree.hashCode()
    }
}

fun IMutableModelTree.asModel(): IMutableModel = MutableModelTreeAsModel(this)
