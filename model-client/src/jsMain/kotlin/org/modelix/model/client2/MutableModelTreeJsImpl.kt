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
import org.modelix.model.api.CompositeModel
import org.modelix.model.api.INodeReference
import org.modelix.model.api.IWritableNode
import org.modelix.model.api.NodeReference
import org.modelix.model.api.withAutoTransactions
import org.modelix.model.mutable.IGenericMutableModelTree
import org.modelix.model.mutable.IMutableModelTree
import org.modelix.model.mutable.NodeInMutableModel
import org.modelix.model.mutable.asModel
import org.modelix.model.mutable.withAutoTransactions
import org.modelix.streams.iterateBlocking

internal class MutableModelTreeJsImpl(
    private val trees: List<IMutableModelTree>,
) : MutableModelTreeJs {
    constructor(tree: IMutableModelTree) : this(listOf(tree))

    private val changeHandlers = mutableSetOf<ChangeHandler>()

    private val model = CompositeModel(trees.map { it.asModel() }).withAutoTransactions()
    private val changeListeners = trees.map { tree ->
        ChangeListener(tree.withAutoTransactions()) { change ->
            changeHandlers.forEach { it(change) }
        }.also { tree.addListener(it) }
        // TODO missing removeListener call
    }

    override val rootNode: INodeJS get() = getRootNodes().single()

    override fun getRootNodes(): Array<INodeJS> {
        return model.executeRead { model.getRootNodes().map { it.toJS() }.toTypedArray() }
    }

    override fun resolveNode(reference: INodeReferenceJS): INodeJS? {
        val referenceObject = NodeReference(reference as String)
        return model.executeRead { model.tryResolveNode(referenceObject)?.toJS() }
    }

    override fun addListener(handler: ChangeHandler) {
        changeHandlers.add(handler)
    }
    override fun removeListener(handler: ChangeHandler) {
        changeHandlers.remove(handler)
    }

    private fun IWritableNode.toJS() = toNodeJs(this.withAutoTransactions().asLegacyNode())
}

internal class ChangeListener(private val tree: IMutableModelTree, private val changeCallback: (ChangeJS) -> Unit) :
    IGenericMutableModelTree.Listener<INodeReference> {

    fun nodeIdToInode(nodeId: INodeReference): INodeJS {
        return toNodeJs(NodeInMutableModel(tree, nodeId).withAutoTransactions().asLegacyNode())
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
