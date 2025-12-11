package org.modelix.model.client2

import INodeJS
import INodeReferenceJS
import org.modelix.datastructures.model.ChildrenChangedEvent
import org.modelix.datastructures.model.ConceptChangedEvent
import org.modelix.datastructures.model.ContainmentChangedEvent
import org.modelix.datastructures.model.IGenericModelTree
import org.modelix.datastructures.model.NodeAddedEvent
import org.modelix.datastructures.model.NodeRemovedEvent
import org.modelix.datastructures.model.PropertyChangedEvent
import org.modelix.datastructures.model.ReferenceChangedEvent
import org.modelix.model.api.AutoTransactionsNode
import org.modelix.model.api.CompositeModel
import org.modelix.model.api.INodeReference
import org.modelix.model.api.IWritableNode
import org.modelix.model.api.NodeReference
import org.modelix.model.mutable.IGenericMutableModelTree
import org.modelix.model.mutable.IMutableModelTree
import org.modelix.model.mutable.NodeInMutableModel
import org.modelix.model.mutable.asModel
import org.modelix.streams.iterateBlocking

internal class MutableModelTreeJsImpl(
    private val trees: List<IMutableModelTree>,
) : MutableModelTreeJs {
    constructor(tree: IMutableModelTree) : this(listOf(tree))

    private val changeHandlers = mutableSetOf<ChangeHandler>()

    private val model = CompositeModel(trees.map { it.asModel() })
    private val changeListeners = trees.map { tree ->
        ChangeListener(tree, model) { change ->
            changeHandlers.forEach { it(change) }
        }.also { tree.addListener(it) }
        // TODO missing removeListener call
    }

    override val rootNode: INodeJS get() = getRootNodes().single()

    override fun getRootNodes(): Array<INodeJS> {
        return model.getRootNodes().map { it.toJS() }.toTypedArray()
    }

    override fun resolveNode(reference: INodeReferenceJS): INodeJS? {
        val referenceObject = NodeReference(reference as String)
        return model.tryResolveNode(referenceObject)?.toJS()
    }

    override fun addListener(handler: ChangeHandler) {
        changeHandlers.add(handler)
    }
    override fun removeListener(handler: ChangeHandler) {
        changeHandlers.remove(handler)
    }

    private fun IWritableNode.toJS() = toNodeJs(AutoTransactionsNode(this, model).asLegacyNode())
}

internal class ChangeListener(
    private val tree: IMutableModelTree,
    private val model: CompositeModel,
    private val changeCallback: (ChangeJS) -> Unit
) : IGenericMutableModelTree.Listener<INodeReference> {

    fun nodeIdToInode(nodeId: INodeReference): INodeJS {
        // Use the composite model to resolve nodes from any tree
        val node = model.tryResolveNode(nodeId)
        if (node == null) {
            // Log or handle the case where node cannot be resolved
            console.log("Warning: Could not resolve node $nodeId in composite model")
            // Fall back to the old behavior for this tree
            return toNodeJs(NodeInMutableModel(tree, nodeId).asLegacyNode())
        }
        return toNodeJs(AutoTransactionsNode(node, model).asLegacyNode())
    }

    override fun treeChanged(oldTree: IGenericModelTree<INodeReference>, newTree: IGenericModelTree<INodeReference>) {
        newTree.getChanges(oldTree, false).iterateBlocking(newTree) {
            when (it) {
                is ConceptChangedEvent<INodeReference> -> changeCallback(ConceptChanged(nodeIdToInode(it.nodeId)))
                is ContainmentChangedEvent<INodeReference> -> changeCallback(ContainmentChanged(nodeIdToInode(it.nodeId)))
                is NodeAddedEvent<INodeReference> -> {}
                is NodeRemovedEvent<INodeReference> -> {}
                is ChildrenChangedEvent<INodeReference> -> changeCallback(ChildrenChanged(nodeIdToInode(it.nodeId), it.role.stringForLegacyApi()))
                is PropertyChangedEvent<INodeReference> -> changeCallback(PropertyChanged(nodeIdToInode(it.nodeId), it.role.stringForLegacyApi()))
                is ReferenceChangedEvent<INodeReference> -> changeCallback(ReferenceChanged(nodeIdToInode(it.nodeId), it.role.stringForLegacyApi()))
            }
        }
    }
}
