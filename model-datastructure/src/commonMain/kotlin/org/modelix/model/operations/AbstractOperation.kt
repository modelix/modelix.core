package org.modelix.model.operations

import org.modelix.datastructures.model.IModelTree
import org.modelix.datastructures.model.asModelTree
import org.modelix.datastructures.objects.IObjectData
import org.modelix.datastructures.objects.ObjectReference
import org.modelix.model.api.ITree

sealed class AbstractOperation : IOperation {

    abstract inner class Applied {
        override fun toString(): String {
            return "applied:" + this@AbstractOperation.toString()
        }
    }

    override fun getObjectReferences(): List<ObjectReference<IObjectData>> = listOf()

    protected fun getNodePosition(tree: IModelTree<Long>, nodeId: Long): PositionInRole {
        val (parent, role) = tree.getContainment(nodeId).getSynchronous()!!
        val index = tree.getChildren(parent, role).asSequence().indexOf(nodeId)
        return PositionInRole(RoleInNode(parent, role), index)
    }

    protected fun getAncestors(tree: ITree, nodeId: Long): LongArray {
        val ancestors: MutableList<Long> = ArrayList()
        var ancestor: Long = tree.getParent(nodeId)
        while (ancestor != 0L) {
            ancestors.add(ancestor)
            ancestor = tree.getParent(ancestor)
        }
        return ancestors.toLongArray()
    }

    protected fun getDetachedNodesEndPosition(tree: ITree): PositionInRole {
        val tree = tree.asModelTree()
        val index = tree.getChildren(DETACHED_ROLE.nodeId, DETACHED_ROLE.role).count().getSynchronous()
        return PositionInRole(DETACHED_ROLE, index)
    }

    companion object {
        protected val DETACHED_ROLE = RoleInNode(ITree.ROOT_ID, ITree.DETACHED_NODES_LINK)
    }
}
