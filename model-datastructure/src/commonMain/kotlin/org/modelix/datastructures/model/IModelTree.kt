package org.modelix.datastructures.model

import org.modelix.model.api.ConceptReference
import org.modelix.model.api.IChildLinkReference
import org.modelix.model.api.IPropertyReference
import org.modelix.model.api.IReferenceLinkReference
import org.modelix.model.api.NodeReference
import org.modelix.streams.IStream

interface IModelTree<NodeId> {
    fun containsNode(nodeId: NodeId): IStream.One<Boolean>
    fun getConceptReference(nodeId: NodeId): IStream.One<ConceptReference>

    fun getParent(nodeId: NodeId): IStream.ZeroOrOne<NodeId>
    fun getRoleInParent(nodeId: NodeId): IStream.ZeroOrOne<IChildLinkReference>
    fun getContainment(nodeId: NodeId): IStream.ZeroOrOne<Pair<NodeId, IChildLinkReference>>

    fun getProperty(nodeId: NodeId, role: IPropertyReference): IStream.ZeroOrOne<String>
    fun getPropertyRoles(nodeId: NodeId): IStream.Many<IPropertyReference>
    fun getProperties(nodeId: NodeId): IStream.Many<Pair<IPropertyReference, String>>

    fun getReferenceTarget(sourceId: NodeId, role: IReferenceLinkReference): IStream.ZeroOrOne<NodeReference>
    fun getReferenceRoles(sourceId: NodeId): IStream.Many<IReferenceLinkReference>
    fun getReferenceTargets(sourceId: NodeId): IStream.Many<Pair<IReferenceLinkReference, NodeReference>>

    fun getChildren(parentId: NodeId): IStream.Many<NodeId>
    fun getChildren(parentId: NodeId, role: IChildLinkReference): IStream.Many<NodeId>
    fun getChildRoles(parentId: NodeId): IStream.Many<IChildLinkReference>
    fun getChildrenAndRoles(parentId: NodeId): IStream.Many<Pair<IChildLinkReference, IStream.Many<NodeId>>>

    fun mutate(operations: Iterable<MutationParameters<NodeId>>): IStream.One<IModelTree<NodeId>>

    fun mutate(operation: MutationParameters<NodeId>): IStream.One<IModelTree<NodeId>> = mutate(listOf(operation))

    fun setProperty(nodeId: NodeId, role: IPropertyReference, value: String?): IStream.One<IModelTree<NodeId>> =
        mutate(MutationParameters.Property(nodeId, role, value))

    fun setReferenceTarget(sourceId: NodeId, role: IReferenceLinkReference, target: NodeReference): IStream.One<IModelTree<NodeId>> =
        mutate(MutationParameters.Reference(sourceId, role, target))

    fun moveNode(newParentId: NodeId, newRole: IChildLinkReference, newIndex: Int, childId: NodeId): IStream.One<IModelTree<NodeId>> =
        mutate(MutationParameters.Move(newParentId, newRole, newIndex, listOf(childId)))

    fun addNewChild(parentId: NodeId, role: IChildLinkReference, index: Int, childId: NodeId, concept: ConceptReference): IStream.One<IModelTree<NodeId>> =
        mutate(MutationParameters.AddNew(parentId, role, index, listOf(childId to concept)))

    fun removeNode(nodeId: NodeId): IStream.One<IModelTree<NodeId>> =
        mutate(MutationParameters.Remove(nodeId))

    fun changeConcept(nodeId: NodeId, concept: ConceptReference): IStream.One<IModelTree<NodeId>> =
        mutate(MutationParameters.Concept(nodeId, concept))
}

sealed class MutationParameters<NodeId> {
    sealed class Node<NodeId> : MutationParameters<NodeId>() {
        abstract val nodeId: NodeId
    }

    data class Property<NodeId>(
        override val nodeId: NodeId,
        val role: IPropertyReference,
        val value: String?,
    ) : Node<NodeId>()

    data class Concept<NodeId>(
        override val nodeId: NodeId,
        val concept: ConceptReference,
    ) : Node<NodeId>()

    data class Reference<NodeId>(
        override val nodeId: NodeId,
        val role: IReferenceLinkReference,
        val target: NodeReference,
    ) : Node<NodeId>()

    sealed class Child<NodeId> : Node<NodeId>() {
        abstract val role: IChildLinkReference
        abstract val index: Int
    }

    data class Move<NodeId>(
        override val nodeId: NodeId,
        override val role: IChildLinkReference,
        override val index: Int,
        val existingChildIds: Iterable<NodeId>,
    ) : Child<NodeId>()

    data class AddNew<NodeId>(
        override val nodeId: NodeId,
        override val role: IChildLinkReference,
        override val index: Int,
        val newIdAndConcept: Iterable<Pair<NodeId, ConceptReference>>,
    ) : Child<NodeId>()

    data class Remove<NodeId>(override val nodeId: NodeId) : Node<NodeId>()
}
