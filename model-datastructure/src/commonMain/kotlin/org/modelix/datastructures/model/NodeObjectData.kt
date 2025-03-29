package org.modelix.datastructures.model

import kotlinx.serialization.Serializable
import org.modelix.datastructures.objects.IDataTypeConfiguration
import org.modelix.datastructures.objects.IObjectData
import org.modelix.datastructures.objects.IObjectDeserializer
import org.modelix.datastructures.objects.IObjectReferenceFactory
import org.modelix.datastructures.objects.ObjectReference
import org.modelix.model.api.ConceptReference
import org.modelix.model.api.IChildLinkReference
import org.modelix.model.api.IPropertyReference
import org.modelix.model.api.IReadableNode
import org.modelix.model.api.IReferenceLinkReference
import org.modelix.model.api.NodeReference
import org.modelix.model.api.meta.NullConcept
import org.modelix.model.api.toSerialized

@Serializable
data class NodeObjectData<NodeId>(
    val id: NodeId,
    val concept: ConceptReference = NullConcept.getReference(),
    val containment: Pair<NodeId, IChildLinkReference>? = null,
    val children: List<NodeId> = emptyList(),
    val properties: List<Pair<IPropertyReference, String>> = emptyList(),
    val references: List<Pair<IReferenceLinkReference, NodeReference>> = emptyList(),
) : IObjectData {

    val parentId: NodeId? get() = containment?.first
    val roleInParent: IChildLinkReference? get() = containment?.second

    override fun serialize(): String {
        TODO("Not yet implemented")
    }

    override fun getDeserializer(): IObjectDeserializer<*> {
        TODO("Not yet implemented")
    }

    override fun getContainmentReferences(): List<ObjectReference<IObjectData>> {
        return emptyList()
    }

    fun getProperty(role: IPropertyReference): String? {
        return properties.find { it.first.matches(role) }?.second
    }

    fun getReferenceTarget(role: IReferenceLinkReference): NodeReference? {
        return references.find { it.first.matches(role) }?.second
    }

    fun withPropertyValue(role: IPropertyReference, value: String?): NodeObjectData<NodeId> {
        var index = properties.indexOfFirst { it.first.matches(role) }
        return if (value == null) {
            if (index < 0) {
                this
            } else {
                copy(properties = properties.take(index) + properties.drop(index + 1))
            }
        } else {
            if (index < 0) {
                copy(properties = properties + (role to value))
            } else {
                copy(properties = properties.take(index) + (role to value) + properties.drop(index + 1))
            }
        }
    }

    class Deserializer<NodeId>(val nodeIdConfig: IDataTypeConfiguration<NodeId>) : IObjectDeserializer<NodeObjectData<NodeId>> {
        override fun deserialize(
            serialized: String,
            referenceFactory: IObjectReferenceFactory,
        ): NodeObjectData<NodeId> {
            TODO("Not yet implemented")
        }
    }
}

fun IReadableNode.toNodeObjectData() = NodeObjectData(
    id = getNodeReference().serialize(),
    concept = getConceptReference(),
    containment = getParent()?.getId()?.let { it to getContainmentLink() },
    children = getAllChildren().map { it.getNodeReference().serialize() },
    properties = getAllProperties(),
    references = getAllReferenceTargetRefs().map { it.first to it.second.toSerialized() },
)
