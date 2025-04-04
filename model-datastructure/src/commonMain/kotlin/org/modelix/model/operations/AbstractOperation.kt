package org.modelix.model.operations

import org.modelix.datastructures.model.IModelTree
import org.modelix.datastructures.model.getAncestors
import org.modelix.datastructures.model.toGlobal
import org.modelix.datastructures.objects.IObjectData
import org.modelix.datastructures.objects.ObjectReference
import org.modelix.model.api.INodeReference
import org.modelix.model.api.IReadableNode
import org.modelix.model.api.ITree
import org.modelix.streams.IStream

sealed class AbstractOperation : IOperation {

    abstract inner class Applied {
        override fun toString(): String {
            return "applied:" + this@AbstractOperation.toString()
        }
    }

    override fun getObjectReferences(): List<ObjectReference<IObjectData>> = listOf()

    protected fun getNodePosition(node: IReadableNode): PositionInRole {
        val parent = requireNotNull(node.getParent()) { "Node has no parent: $node" }
        val role = node.getContainmentLink()
        val index = parent.getChildren(role).indexOf(node)
        return PositionInRole(RoleInNode(parent.getNodeReference(), role), index)
    }

    protected fun getNodePosition(tree: IModelTree, nodeId: INodeReference): PositionInRole {
        val (parent, role) = requireNotNull(tree.getContainment(nodeId).getBlocking()) { "Node has no parent: $nodeId" }
        val index = tree.getChildren(parent, role).toList().getBlocking().indexOf(nodeId)
        return PositionInRole(RoleInNode(parent, role), index)
    }

    @Deprecated("Use tree.getAncestors")
    protected fun getAncestors(tree: IModelTree, nodeId: INodeReference): IStream.Many<INodeReference> {
        return tree.getAncestors(nodeId, false)
    }

    protected fun getDetachedNodesEndPosition(tree: IModelTree): PositionInRole {
        val detachedRole = RoleInNode(tree.getRootNodeId(), ITree.DETACHED_NODES_LINK)
        val index = tree.getChildren(detachedRole.nodeId.toGlobal(tree.getId()), detachedRole.role).count().getSynchronous()
        return PositionInRole(detachedRole, index)
    }
}
