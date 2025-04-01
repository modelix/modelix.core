package org.modelix.datastructures.model

import kotlinx.serialization.Contextual
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient
import kotlinx.serialization.modules.SerializersModule
import org.modelix.datastructures.objects.IDataTypeConfiguration
import org.modelix.datastructures.objects.IObjectData
import org.modelix.datastructures.objects.IObjectDeserializer
import org.modelix.datastructures.objects.IObjectReferenceFactory
import org.modelix.datastructures.objects.LongDataTypeConfiguration
import org.modelix.datastructures.objects.ObjectReference
import org.modelix.datastructures.objects.asKSerializer
import org.modelix.datastructures.serialization.SplitJoinFormat
import org.modelix.datastructures.serialization.TransformingSerializer
import org.modelix.kotlin.utils.DelicateModelixApi
import org.modelix.model.TreeId
import org.modelix.model.api.ConceptReference
import org.modelix.model.api.IChildLinkReference
import org.modelix.model.api.INodeReference
import org.modelix.model.api.IPropertyReference
import org.modelix.model.api.IReadableNode
import org.modelix.model.api.IReferenceLinkReference
import org.modelix.model.api.NullChildLinkReference
import org.modelix.model.api.meta.NullConcept

@Serializable
data class NodeObjectData<NodeId>(
    @Transient val deserializer: Deserializer<NodeId>? = null,
    val id: NodeId,
    val concept: ConceptReference? = null,
    val containment: Pair<NodeId, IChildLinkReference>? = null,
    val children: List<NodeId> = emptyList(),
    val properties: List<Pair<String, String>> = emptyList(),
    val references: List<Pair<String, @Contextual INodeReference>> = emptyList(),
) : IObjectData {

    init {
        require(concept == null || concept.getUID() != NullConcept.getUID())
        require(containment == null || containment.second == NullChildLinkReference || containment.second.getIdOrNameOrNull() != "null")
    }

    val parentId: NodeId? get() = containment?.first
    val roleInParent: IChildLinkReference get() = containment?.second ?: NullChildLinkReference

    override fun serialize(): String {
        return deserializer!!.serialFormat.encodeToString(deserializer.kSerializer, this)
    }

    override fun getDeserializer(): IObjectDeserializer<*> {
        return deserializer!!
    }

    override fun getContainmentReferences(): List<ObjectReference<IObjectData>> {
        return emptyList()
    }

    fun getProperty(role: IPropertyReference): String? {
        return properties.find { role.matches(it.first) }?.second
    }

    fun getReferenceTarget(role: IReferenceLinkReference): INodeReference? {
        return references.find { role.matches(it.first) }?.second
    }

    fun withPropertyValue(role: IPropertyReference, value: String?): NodeObjectData<NodeId> {
        var index = properties.indexOfFirst { role.matches(it.first) }
        return if (value == null) {
            if (index < 0) {
                this
            } else {
                copy(properties = properties.take(index) + properties.drop(index + 1))
            }
        } else {
            // persist ID only to prevent ObjectHash changes when metamodel elements are renamed
            @OptIn(DelicateModelixApi::class)
            if (index < 0) {
                copy(properties = properties + (role.getIdOrName() to value))
            } else {
                copy(properties = properties.take(index) + (role.getIdOrName() to value) + properties.drop(index + 1))
            }
        }
    }

    fun withReferenceTarget(role: IReferenceLinkReference, value: INodeReference?): NodeObjectData<NodeId> {
        var index = references.indexOfFirst { role.matches(it.first) }
        return if (value == null) {
            if (index < 0) {
                this
            } else {
                copy(references = references.take(index) + references.drop(index + 1))
            }
        } else {
            // persist ID only to prevent ObjectHash changes when metamodel elements are renamed
            @OptIn(DelicateModelixApi::class)
            if (index < 0) {
                copy(references = references + (role.getIdOrName() to value))
            } else {
                copy(references = references.take(index) + (role.getIdOrName() to value) + references.drop(index + 1))
            }
        }
    }

    fun withChildRemoved(childId: NodeId): NodeObjectData<NodeId> {
        return copy(children = children.filterNot { deserializer!!.nodeIdTypeConfig.equal(it, childId) })
    }

    class Deserializer<NodeId>(
        val nodeIdTypeConfig: IDataTypeConfiguration<NodeId>,
        val treeId: TreeId,
    ) : IObjectDeserializer<NodeObjectData<NodeId>> {
        val referenceTypeConfig = LegacyNodeReferenceDataTypeConfig(treeId)
        val serialFormat = SplitJoinFormat(
            SerializersModule {
                contextual(INodeReference::class, referenceTypeConfig.asKSerializer())
            },
        )

        // val kSerializer = NodeObjectData.serializer(nodeIdTypeConfig.asKSerializer())
        val kSerializer = object : TransformingSerializer<NodeObjectData<NodeId>, LegacyCompatibleFormat<NodeId, INodeReference>>(
            LegacyCompatibleFormat.serializer(nodeIdTypeConfig.asKSerializer(), referenceTypeConfig.asKSerializer()),
        ) {
            /**
             * The legacy format used the non-nullable type `Long` for IDs and used 0 if a node had no parent.
             */
            fun encodeNullId(id: NodeId?) = if (id == null && nodeIdTypeConfig is LongDataTypeConfiguration) (0L as NodeId) else id
            fun decodeNullId(id: NodeId?) = id.takeIf { it != 0L }

            override fun convertToSerialized(value: NodeObjectData<NodeId>): LegacyCompatibleFormat<NodeId, INodeReference> {
                return LegacyCompatibleFormat(
                    id = value.id,
                    concept = value.concept?.takeIf { it != NullConcept.getReference() },
                    parent = encodeNullId(value.parentId),
                    role = value.roleInParent.getIdOrNameOrNull(),
                    children = value.children,
                    properties = value.properties.sortedBy { it.first }.toMap(),
                    references = value.references.sortedBy { it.first }.toMap(),
                )
            }

            override fun convertFromSerialized(value: LegacyCompatibleFormat<NodeId, INodeReference>): NodeObjectData<NodeId> {
                return NodeObjectData(
                    deserializer = this@Deserializer,
                    id = value.id,
                    concept = value.concept,
                    containment = decodeNullId(value.parent)?.let { it to IChildLinkReference.fromString(value.role) },
                    children = value.children,
                    properties = value.properties.toList(),
                    references = value.references.toList(),
                )
            }
        }
        override fun deserialize(
            serialized: String,
            referenceFactory: IObjectReferenceFactory,
        ): NodeObjectData<NodeId> {
            return serialFormat.decodeFromString(kSerializer, serialized)
                .copy(deserializer = this)
        }
    }
}

@Serializable
data class LegacyCompatibleFormat<NodeId, ReferenceType>(
    val id: NodeId,
    val concept: ConceptReference?,
    val parent: NodeId?,
    val role: String?,
    val children: List<NodeId>,
    val properties: Map<String, String>,
    val references: Map<String, ReferenceType>,
)

fun IReadableNode.toNodeObjectData(): NodeObjectData<INodeReference> {
    // persist ID only to prevent ObjectHash changes when metamodel elements are renamed
    @OptIn(DelicateModelixApi::class)
    return NodeObjectData(
        deserializer = NodeObjectData.Deserializer(NodeReferenceDataTypeConfig(), getTreeId()),
        id = getNodeReference(),
        concept = getConceptReference(),
        containment = getParent()?.let { it.getNodeReference() to getContainmentLink() },
        children = getAllChildren().map { it.getNodeReference() },
        properties = getAllProperties().map { it.first.getIdOrName() to it.second },
        references = getAllReferenceTargetRefs().map { it.first.getIdOrName() to it.second },
    )
}

fun IReadableNode.getTreeId(): TreeId {
    return when (this) {
        else -> throw IllegalArgumentException("Unknown node type: $this")
    }
}
