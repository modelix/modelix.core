package org.modelix.datastructures.model

import org.modelix.datastructures.objects.IDataTypeConfiguration
import org.modelix.model.TreeId
import org.modelix.model.api.INodeReference
import org.modelix.model.api.LocalPNodeReference
import org.modelix.model.api.NodeReference
import org.modelix.model.api.PNodeReference
import org.modelix.model.persistent.CPNodeRef

class NodeReferenceDataTypeConfig : IDataTypeConfiguration<INodeReference> {
    override fun serialize(element: INodeReference): String {
        return element.serialize()
    }

    override fun deserialize(serialized: String): INodeReference {
        return NodeReference(serialized)
    }

    override fun hashCode32(element: INodeReference): Int {
        return element.serialize().hashCode()
    }

    override fun compare(a: INodeReference, b: INodeReference): Int {
        return a.serialize().compareTo(b.serialize())
    }
}

class LegacyNodeReferenceDataTypeConfig(val treeId: TreeId) : IDataTypeConfiguration<INodeReference> {
    override fun serialize(element: INodeReference): String {
        return PNodeReference.tryConvert(element)
            ?.takeIf { it.treeId == treeId.id }
            ?.let { it.id.toULong().toString(16) }
            ?: element.serialize()
    }

    override fun deserialize(serialized: String): INodeReference {
        serialized.toULongOrNull(16)?.let { return PNodeReference(it.toLong(), treeId.id) }

        val parsedRef = if (serialized.startsWith("G") || serialized.startsWith("M")) {
            val legacyRef = CPNodeRef.fromString(serialized)
            when (legacyRef) {
                is CPNodeRef.ForeignRef -> NodeReference(serialized)
                is CPNodeRef.GlobalRef -> return PNodeReference(legacyRef.elementId, legacyRef.treeId)
                is CPNodeRef.LocalRef -> return PNodeReference(legacyRef.elementId, treeId.id)
            }
        } else {
            NodeReference(serialized)
        }
        return PNodeReference.tryConvert(parsedRef) ?: parsedRef
    }

    override fun hashCode32(element: INodeReference): Int {
        return element.serialize().hashCode()
    }

    override fun compare(a: INodeReference, b: INodeReference): Int {
        return a.serialize().compareTo(b.serialize())
    }
}

fun CPNodeRef.Companion.fromNodeReference(ref: INodeReference, treeId: TreeId): CPNodeRef {
    return when (ref) {
        is LocalPNodeReference -> CPNodeRef.local(ref.id)
        is PNodeReference -> if (ref.treeId == treeId.id) local(ref.id) else global(ref.treeId, ref.id)
        else -> foreign(ref.serialize())
    }
}
