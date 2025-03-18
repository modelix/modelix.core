package org.modelix.model.operations

import org.modelix.model.api.ITree
import org.modelix.model.objects.IObjectData
import org.modelix.model.objects.ObjectReference

sealed class AbstractOperation : IOperation {

    abstract inner class Applied {
        override fun toString(): String {
            return "applied:" + this@AbstractOperation.toString()
        }
    }

    override fun getObjectReferences(): List<ObjectReference<IObjectData>> = listOf()

    protected fun getNodePosition(tree: ITree, nodeId: Long): PositionInRole {
        val parent = tree.getParent(nodeId)
        val role = tree.getRole(nodeId)
        val index = tree.getChildren(parent, role).indexOf(nodeId)
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
        val index = tree.getChildren(DETACHED_ROLE.nodeId, DETACHED_ROLE.role).count()
        return PositionInRole(DETACHED_ROLE, index)
    }

    companion object {
        protected val DETACHED_ROLE = RoleInNode(ITree.ROOT_ID, ITree.DETACHED_NODES_ROLE)
    }
}
