package org.modelix.datastructures.model

import org.modelix.datastructures.autoResolveValues
import org.modelix.datastructures.hamt.HamtInternalNode
import org.modelix.datastructures.hamt.HamtNode
import org.modelix.datastructures.hamt.HamtTree
import org.modelix.datastructures.objects.IDataTypeConfiguration
import org.modelix.datastructures.objects.IObjectGraph
import org.modelix.datastructures.objects.LongDataTypeConfiguration
import org.modelix.datastructures.objects.ObjectReferenceDataTypeConfiguration
import org.modelix.datastructures.objects.asObject
import org.modelix.datastructures.patricia.PatriciaTrie
import org.modelix.datastructures.patricia.PatriciaTrieConfig
import org.modelix.model.TreeId
import org.modelix.model.api.INodeReference
import org.modelix.model.api.ITree
import org.modelix.model.api.PNodeReference
import org.modelix.streams.getBlocking

abstract class ModelTreeBuilder<NodeId> private constructor(protected val common: Common = Common()) {
    protected class Common {
        var graph: IObjectGraph = IObjectGraph.FREE_FLOATING
        var treeId: TreeId = TreeId.random()
        var storeRoleIds: Boolean = true
    }

    private class Int64Builder(common: Common) : ModelTreeBuilder<Long>(common) {
        override fun build(): IGenericModelTree<Long> {
            val nodeIdType = LongDataTypeConfiguration()
            val root = NodeObjectData<Long>(
                deserializer = NodeObjectData.Deserializer(common.graph, nodeIdType, common.treeId),
                id = ITree.ROOT_ID,
                concept = null,
                containment = null,
            ).asObject(common.graph)

            val config = HamtNode.Config(
                graph = common.graph,
                keyConfig = nodeIdType,
                valueConfig = ObjectReferenceDataTypeConfiguration(common.graph, NodeObjectData.Deserializer(common.graph, nodeIdType, common.treeId)),
            )
            return HamtInternalNode.createEmpty(config)
                .put(root.data.id, root.ref, common.graph)
                .orNull()
                .getBlocking(common.graph)!!
                .let { HamtTree(it) }
                .autoResolveValues()
                .asModelTree(common.treeId, common.storeRoleIds)
        }
    }
    private class NodeRefBuilder(common: Common) : ModelTreeBuilder<INodeReference>(common) {
        override fun build(): IGenericModelTree<INodeReference> {
            val nodeIdType = NodeReferenceDataTypeConfig()
            val root = NodeObjectData<INodeReference>(
                deserializer = NodeObjectData.Deserializer(common.graph, nodeIdType, common.treeId),
                id = PNodeReference(ITree.ROOT_ID, common.treeId.id),
                concept = null,
                containment = null,
            ).asObject(common.graph)
            val config = PatriciaTrieConfig(
                graph = common.graph,
                keyConfig = nodeIdType,
                valueConfig = ObjectReferenceDataTypeConfiguration(common.graph, NodeObjectData.Deserializer(common.graph, nodeIdType, common.treeId)),
            )
            return PatriciaTrie(config)
                .put(root.data.id, root.ref)
                .getBlocking(common.graph)
                .autoResolveValues()
                .asModelTree(common.treeId, common.storeRoleIds)
        }
    }

    private var nodeIdType: IDataTypeConfiguration<NodeId>? = null

    fun graph(value: IObjectGraph) = also {
        common.graph = value
    }

    @Deprecated("")
    fun storeRoleNames(value: Boolean = true) = also {
        common.storeRoleIds = !value
    }

    fun treeId(id: TreeId) = also {
        common.treeId = id
    }

    fun treeId(id: String) = also {
        common.treeId = TreeId.fromLegacyId(id)
    }

    fun withInt64Ids(): ModelTreeBuilder<Long> = Int64Builder(common)

    fun withNodeReferenceIds(): ModelTreeBuilder<INodeReference> = NodeRefBuilder(common)

    abstract fun build(): IGenericModelTree<NodeId>

    companion object {
        internal fun newWithNodeReferenceIds(): ModelTreeBuilder<INodeReference> = NodeRefBuilder(Common())
        internal fun newWithInt64Ids(): ModelTreeBuilder<Long> = Int64Builder(Common())
    }
}
