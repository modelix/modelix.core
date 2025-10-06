package org.modelix.datastructures.model

import org.modelix.datastructures.objects.ObjectHash
import org.modelix.kotlin.utils.DelicateModelixApi
import org.modelix.model.IVersion
import org.modelix.model.api.ConceptReference
import org.modelix.model.api.IChildLinkReference
import org.modelix.model.api.INodeReference
import org.modelix.model.api.IPropertyReference
import org.modelix.model.api.IReferenceLinkReference
import org.modelix.model.api.meta.NullConcept
import org.modelix.model.lazy.CLVersion
import org.modelix.model.operations.AddNewChildSubtreeOp
import org.modelix.model.operations.AddNewChildrenOp
import org.modelix.model.operations.BulkUpdateOp
import org.modelix.model.operations.DeleteNodeOp
import org.modelix.model.operations.IOperation
import org.modelix.model.operations.MoveNodeOp
import org.modelix.model.operations.NoOp
import org.modelix.model.operations.RevertToOp
import org.modelix.model.operations.SetConceptOp
import org.modelix.model.operations.SetPropertyOp
import org.modelix.model.operations.SetReferenceOp
import org.modelix.model.operations.UndoOp
import org.modelix.streams.IStream
import org.modelix.streams.plus

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
        val target: INodeReference?,
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

/**
 * Very limited support.
 * Merge commits are not allowed. There has to be a single path between the two versions.
 * Higher level operations, that would require deriving the operations by diffing the model data, are not supported.
 */
@DelicateModelixApi
fun IVersion.historyAsMutationParameters(oldVersionHash: ObjectHash): IStream.Many<MutationParameters<INodeReference>> {
    this as CLVersion

    if (this.isMerge()) throw DiffNotSupportedException("Merge commits are not supported")
    if (this.getObjectHash() == oldVersionHash) return IStream.empty()
    val parentOperations = this.getBaseVersionLater().orNull().flatMapOrdered { parentVersion ->
        if (parentVersion == null) throw DiffNotSupportedException("$oldVersionHash not found in the history")
        parentVersion.historyAsMutationParameters(oldVersionHash)
    }
    val ownOperations = this.operationsAsStream().flatMapOrdered { it.asMutationParameters() }
    return parentOperations + ownOperations
}

private fun IOperation.asMutationParameters(): IStream.Many<MutationParameters<INodeReference>> {
    return when (this) {
        is AddNewChildSubtreeOp -> decompress().flatMapOrdered { it.asMutationParameters() }
        is AddNewChildrenOp -> IStream.of(MutationParameters.AddNew(position.nodeId, position.role, position.index, childIdsAndConcepts))
        is BulkUpdateOp -> throw DiffNotSupportedException("Cannot compute diff for versions containing BulkUpdateOp")
        is DeleteNodeOp -> IStream.of(MutationParameters.Remove(childId))
        is MoveNodeOp -> IStream.of(MutationParameters.Move(targetPosition.nodeId, targetPosition.role, targetPosition.index, listOf(childId)))
        is NoOp -> IStream.empty()
        is RevertToOp -> throw DiffNotSupportedException("Cannot compute diff for versions containing RevertToOp")
        is SetConceptOp -> IStream.of(MutationParameters.Concept(nodeId, concept ?: NullConcept.getReference()))
        is SetPropertyOp -> IStream.of(MutationParameters.Property(nodeId, role, value))
        is SetReferenceOp -> IStream.of(MutationParameters.Reference(sourceId, role, target))
        is UndoOp -> throw DiffNotSupportedException("Cannot compute diff for versions containing UndoOp")
    }
}

class DiffNotSupportedException(message: String) : IllegalStateException(message)
