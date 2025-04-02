package org.modelix.datastructures.model

import org.modelix.datastructures.IPersistentMap
import org.modelix.datastructures.objects.Object
import org.modelix.datastructures.objects.asObject
import org.modelix.datastructures.objects.upcast
import org.modelix.model.TreeId
import org.modelix.model.api.INodeReference
import org.modelix.model.api.ITree
import org.modelix.model.api.PNodeReference
import org.modelix.model.persistent.CPTree
import kotlin.jvm.JvmName

/**
 * Legacy storage with new API that hides details about the type of IDs that's used internally.
 * With the previous API, the ID type was only hidden at the mutable nodes API layer, but for persistent model trees
 * there was only an interface that used 64-bit integers.
 */
class Int64ModelTree(nodesMap: IPersistentMap<Long, NodeObjectData<Long>>, treeId: TreeId) :
    GenericModelTree<Long>(nodesMap, treeId) {
    override fun getRootNodeId(): Long = ITree.ROOT_ID
    override fun asObject(): Object<CPTree> {
        return CPTree(
            id = getId(),
            int64Hamt = nodesMap.asObject().ref.upcast(),
            trieWithNodeRefIds = null,
            usesRoleIds = true,
        ).asObject(graph)
    }
    override fun withNewMap(newNodesMap: IPersistentMap<Long, NodeObjectData<Long>>) = Int64ModelTree(newNodesMap, getId())
    override fun createNodeReference(nodeId: Long): INodeReference {
        return PNodeReference(nodeId, getId().id)
    }
}

class DefaultModelTree(nodesMap: IPersistentMap<INodeReference, NodeObjectData<INodeReference>>, treeId: TreeId) :
    GenericModelTree<INodeReference>(nodesMap, treeId) {
    override fun getRootNodeId(): INodeReference = PNodeReference(ITree.ROOT_ID, getId().id)
    override fun asObject(): Object<CPTree> {
        return CPTree(
            id = getId(),
            int64Hamt = null,
            trieWithNodeRefIds = nodesMap.asObject().ref.upcast(),
            usesRoleIds = true,
        ).asObject(graph)
    }
    override fun withNewMap(newNodesMap: IPersistentMap<INodeReference, NodeObjectData<INodeReference>>) =
        DefaultModelTree(newNodesMap, getId())

    override fun createNodeReference(nodeId: INodeReference): INodeReference {
        return nodeId
    }
}

class NodeNotFoundException(nodeId: Any?) : RuntimeException("Node doesn't exist: $nodeId")

@JvmName("asModelTreeWithNodeReferences")
fun IPersistentMap<INodeReference, NodeObjectData<INodeReference>>.asModelTree(treeId: TreeId): IModelTree<INodeReference> {
    return DefaultModelTree(this, treeId)
}

@JvmName("asModelTreeWithInt64")
fun IPersistentMap<Long, NodeObjectData<Long>>.asModelTree(treeId: TreeId): IModelTree<Long> {
    return Int64ModelTree(this, treeId)
}
