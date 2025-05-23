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
import org.modelix.model.api.INodeReference
import org.modelix.model.api.INodeReferenceSerializer
import org.modelix.model.mutable.IGenericMutableModelTree
import org.modelix.model.mutable.IMutableModelTree
import org.modelix.model.mutable.NodeInMutableModel
import org.modelix.model.mutable.asModel
import org.modelix.model.mutable.getRootNode
import org.modelix.streams.iterateBlocking

internal class MutableModelTreeJsImpl(
    private val tree: IMutableModelTree,
) : MutableModelTreeJs {

    private val changeHandlers = mutableSetOf<ChangeHandler>()

    private val jsRootNode = toNodeJs(tree.getRootNode().asLegacyNode())
    private val changeListener = ChangeListener(tree) { change ->
        changeHandlers.forEach {
                changeHandler ->
            changeHandler(change)
        }
    }

    init {
        tree.addListener(changeListener)
    }

    override val rootNode: INodeJS
        get() {
            return jsRootNode
        }

    override fun resolveNode(reference: INodeReferenceJS): INodeJS? {
        val referenceObject = INodeReferenceSerializer.deserialize(reference as String)
        return tree.asModel().tryResolveNode(referenceObject)?.asLegacyNode()?.let(::toNodeJs)
    }

    override fun addListener(handler: ChangeHandler) {
        changeHandlers.add(handler)
    }
    override fun removeListener(handler: ChangeHandler) {
        changeHandlers.remove(handler)
    }
}

internal class ChangeListener(private val tree: IMutableModelTree, private val changeCallback: (ChangeJS) -> Unit) :
    IGenericMutableModelTree.Listener<INodeReference> {

    fun nodeIdToInode(nodeId: INodeReference): INodeJS {
        return toNodeJs(NodeInMutableModel(tree, nodeId).asLegacyNode())
    }

    override fun treeChanged(oldTree: IGenericModelTree<INodeReference>, newTree: IGenericModelTree<INodeReference>) {
        if (oldTree == null) {
            return
        }
        newTree.getChanges(oldTree, false).iterateBlocking(newTree) {
            when (it) {
                is ConceptChangedEvent<INodeReference> -> changeCallback(ConceptChanged(nodeIdToInode(it.nodeId)))
                is ContainmentChangedEvent<INodeReference> -> changeCallback(ContainmentChanged(nodeIdToInode(it.nodeId)))
                is NodeAddedEvent<INodeReference> -> {}
                is NodeRemovedEvent<INodeReference> -> {}
                is ChildrenChangedEvent<INodeReference> -> changeCallback(ChildrenChanged(nodeIdToInode(it.nodeId), it.role.getIdOrNameOrNull()))
                is PropertyChangedEvent<INodeReference> -> changeCallback(PropertyChanged(nodeIdToInode(it.nodeId), it.role.getIdOrName()))
                is ReferenceChangedEvent<INodeReference> -> changeCallback(ReferenceChanged(nodeIdToInode(it.nodeId), it.role.getIdOrName()))
            }
        }
    }
}
