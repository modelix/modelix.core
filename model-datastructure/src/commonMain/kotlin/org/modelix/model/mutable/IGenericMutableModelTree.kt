package org.modelix.model.mutable

import org.modelix.datastructures.model.IGenericModelTree
import org.modelix.datastructures.model.MutationParameters
import org.modelix.model.TreeId
import org.modelix.model.api.ConceptReference
import org.modelix.model.api.IChildLinkReference
import org.modelix.model.api.INodeReference
import org.modelix.model.api.IPropertyReference
import org.modelix.model.api.IReferenceLinkReference

typealias IMutableModelTree = IGenericMutableModelTree<INodeReference>

interface IGenericMutableModelTree<NodeId> {
    fun getId(): TreeId
    fun getIdGenerator(): INodeIdGenerator<NodeId>
    fun <R> runRead(body: (Transaction<NodeId>) -> R): R
    fun <R> runWrite(body: (WriteTransaction<NodeId>) -> R): R
    fun canRead(): Boolean
    fun canWrite(): Boolean
    fun getTransaction(): Transaction<NodeId>
    fun getWriteTransaction(): WriteTransaction<NodeId>
    fun addListener(listener: Listener<NodeId>)
    fun removeListener(listener: Listener<NodeId>)

    interface Transaction<NodeId> {
        val tree: IGenericModelTree<NodeId>
    }

    interface WriteTransaction<NodeId> : Transaction<NodeId> {
        override var tree: IGenericModelTree<NodeId>
        fun getIdGenerator(): INodeIdGenerator<NodeId>
        fun mutate(parameters: MutationParameters<NodeId>)
    }

    interface Listener<NodeId> {
        fun treeChanged(oldTree: IGenericModelTree<NodeId>, newTree: IGenericModelTree<NodeId>)
    }
}

fun <NodeId> IGenericMutableModelTree.WriteTransaction<NodeId>.addNewChild(
    parentId: NodeId,
    role: IChildLinkReference,
    index: Int,
    childId: NodeId,
    childConcept: ConceptReference,
) = mutate(
    MutationParameters.AddNew(
        nodeId = parentId,
        role = role,
        index = index,
        newIdAndConcept = listOf(childId to childConcept),
    ),
)

fun <NodeId> IGenericMutableModelTree.WriteTransaction<NodeId>.moveChildren(
    parentId: NodeId,
    role: IChildLinkReference,
    index: Int,
    childIds: List<NodeId>,
) = mutate(
    MutationParameters.Move(
        nodeId = parentId,
        role = role,
        index = index,
        existingChildIds = childIds,
    ),
)

fun <NodeId> IGenericMutableModelTree.WriteTransaction<NodeId>.moveChild(
    parentId: NodeId,
    role: IChildLinkReference,
    index: Int,
    childId: NodeId,
) = mutate(
    MutationParameters.Move(
        nodeId = parentId,
        role = role,
        index = index,
        existingChildIds = listOf(childId),
    ),
)

fun <NodeId> IGenericMutableModelTree<NodeId>.setProperty(
    nodeId: NodeId,
    role: IPropertyReference,
    value: String?,
) = getWriteTransaction().setProperty(nodeId, role, value)

fun <NodeId> IGenericMutableModelTree.WriteTransaction<NodeId>.setProperty(
    nodeId: NodeId,
    role: IPropertyReference,
    value: String?,
) = mutate(
    MutationParameters.Property(
        nodeId = nodeId,
        role = role,
        value = value,
    ),
)

fun <NodeId> IGenericMutableModelTree<NodeId>.setReferenceTarget(
    nodeId: NodeId,
    role: IReferenceLinkReference,
    target: INodeReference?,
) = getWriteTransaction().setReferenceTarget(nodeId, role, target)

fun <NodeId> IGenericMutableModelTree.WriteTransaction<NodeId>.setReferenceTarget(
    nodeId: NodeId,
    role: IReferenceLinkReference,
    target: INodeReference?,
) = mutate(
    MutationParameters.Reference(
        nodeId = nodeId,
        role = role,
        target = target,
    ),
)

fun <NodeId> IGenericMutableModelTree.WriteTransaction<NodeId>.setConcept(
    nodeId: NodeId,
    concept: ConceptReference,
) = mutate(
    MutationParameters.Concept(
        nodeId = nodeId,
        concept = concept,
    ),
)

fun <NodeId> IGenericMutableModelTree.WriteTransaction<NodeId>.removeNode(
    nodeId: NodeId,
) = mutate(
    MutationParameters.Remove(
        nodeId = nodeId,
    ),
)

fun <NodeId> IGenericMutableModelTree.Transaction<NodeId>.getRootNodeId() = tree.getRootNodeId()
fun IGenericMutableModelTree.Transaction<*>.treeId() = tree.getId()
