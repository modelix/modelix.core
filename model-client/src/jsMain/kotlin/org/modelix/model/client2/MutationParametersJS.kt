package org.modelix.model.client2

import org.modelix.datastructures.model.MutationParameters
import org.modelix.model.api.INodeReference

@JsExport
sealed class MutationParametersJS {
    sealed class Node : MutationParametersJS() {
        abstract val nodeId: String
    }

    class Property(
        override val nodeId: String,
        val role: String,
        val value: String?,
    ) : Node()

    class Concept(
        override val nodeId: String,
        val concept: String,
    ) : Node()

    class Reference(
        override val nodeId: String,
        val role: String,
        val target: String?,
    ) : Node()

    sealed class Child : Node() {
        abstract val role: String
        abstract val index: Int
    }

    class Move(
        override val nodeId: String,
        override val role: String,
        override val index: Int,
        val existingChildIds: Array<String>,
    ) : Child()

    class AddNew(
        override val nodeId: String,
        override val role: String,
        override val index: Int,
        val newIds: Array<String>,
        val newConcepts: Array<String>,
    ) : Child()

    class Remove(override val nodeId: String) : Node()
}

fun MutationParameters<INodeReference>.toJS() = when (this) {
    is MutationParameters.AddNew<INodeReference> -> MutationParametersJS.AddNew(
        nodeId = nodeId.serialize(),
        role = role.stringForLegacyApi() ?: "null",
        index = index,
        newIds = newIdAndConcept.map { it.first.serialize() }.toTypedArray(),
        newConcepts = newIdAndConcept.map { it.second.getUID() }.toTypedArray(),
    )
    is MutationParameters.Move<INodeReference> -> MutationParametersJS.Move(
        nodeId = nodeId.serialize(),
        role = role.stringForLegacyApi() ?: "null",
        index = index,
        existingChildIds = existingChildIds.map { it.serialize() }.toTypedArray(),
    )
    is MutationParameters.Concept<INodeReference> -> MutationParametersJS.Concept(
        nodeId = nodeId.serialize(),
        concept = concept.getUID(),
    )
    is MutationParameters.Property<INodeReference> -> MutationParametersJS.Property(
        nodeId = nodeId.serialize(),
        role = role.stringForLegacyApi(),
        value = value,
    )
    is MutationParameters.Reference<INodeReference> -> MutationParametersJS.Reference(
        nodeId = nodeId.serialize(),
        role = role.stringForLegacyApi(),
        target = target?.serialize(),
    )
    is MutationParameters.Remove<INodeReference> -> MutationParametersJS.Remove(
        nodeId = nodeId.serialize(),
    )
}
