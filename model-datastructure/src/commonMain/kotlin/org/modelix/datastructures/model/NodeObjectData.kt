package org.modelix.datastructures.model

import kotlinx.serialization.Contextual
import kotlinx.serialization.Serializable
import kotlinx.serialization.modules.SerializersModule
import org.modelix.datastructures.list.LargeList
import org.modelix.datastructures.list.LargeListConfig
import org.modelix.datastructures.list.LargeListKSerializer
import org.modelix.datastructures.objects.IDataTypeConfiguration
import org.modelix.datastructures.objects.IObjectData
import org.modelix.datastructures.objects.IObjectDeserializer
import org.modelix.datastructures.objects.IObjectGraph
import org.modelix.datastructures.objects.IObjectReferenceFactory
import org.modelix.datastructures.objects.LongDataTypeConfiguration
import org.modelix.datastructures.objects.Object
import org.modelix.datastructures.objects.ObjectReference
import org.modelix.datastructures.objects.asKSerializer
import org.modelix.datastructures.objects.getDescendantsAndSelf
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
import org.modelix.model.api.IRoleReference
import org.modelix.model.api.NullChildLinkReference
import org.modelix.model.api.meta.NullConcept
import org.modelix.streams.IStream

data class NodeObjectData<NodeId>(
    val deserializer: Deserializer<NodeId>? = null,
    val id: NodeId,
    val concept: ConceptReference? = null,
    val containment: Pair<NodeId, String?>? = null,
    val children: LargeList<NodeId>? = null,
    val properties: List<Pair<String, String>> = emptyList(),
    val references: List<Pair<String, INodeReference>> = emptyList(),
) : IObjectData {

    init {
        // ensure consistent encoding nullable values
        require(concept == null || concept.getUID() != NullConcept.getUID())
        require(
            containment == null || containment.second == null || !IChildLinkReference.fromString(containment.second).matches(
                NullChildLinkReference,
            ),
        )
    }

    val parentId: NodeId? get() = containment?.first
    val roleInParent: IChildLinkReference get() = containment?.second?.let { IChildLinkReference.fromString(it) } ?: NullChildLinkReference

    private fun getRoleInParentAsString(): String? = containment?.second

    fun getChildIds(): IStream.Many<NodeId> = children?.getElements() ?: IStream.empty()

    override fun serialize(): String {
        return deserializer!!.serialFormat.encodeToString(deserializer.kSerializer, this)
    }

    override fun getDeserializer(): IObjectDeserializer<*> {
        return deserializer!!
    }

    override fun getContainmentReferences(): List<ObjectReference<IObjectData>> {
        return children?.getContainmentReferences() ?: emptyList()
    }

    fun getProperty(role: IPropertyReference): String? {
        return properties.find { role.matches(it.first) }?.second
    }

    fun getReferenceTarget(role: IReferenceLinkReference): INodeReference? {
        return references.find { role.matches(it.first) }?.second
    }

    fun withPropertyValue(role: IPropertyReference, useRoleIds: Boolean, value: String?): NodeObjectData<NodeId> {
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
            val newProperties = if (index < 0) {
                properties + (role.chooseIdOrName(useRoleIds) to value)
            } else {
                properties.take(index) + (role.chooseIdOrName(useRoleIds) to value) + properties.drop(index + 1)
            }
            // sorted to get a stable ObjectHash and avoid non-determinism in algorithms working with the model (e.g. sync)
            copy(properties = newProperties.sortedBy { it.first })
        }
    }

    fun withReferenceTarget(role: IReferenceLinkReference, useRoleIds: Boolean, value: INodeReference?): NodeObjectData<NodeId> {
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
            val newReferences = if (index < 0) {
                references + (role.chooseIdOrName(useRoleIds) to value)
            } else {
                references.take(index) + (role.chooseIdOrName(useRoleIds) to value) + references.drop(index + 1)
            }
            // sorted to get a stable ObjectHash and avoid non-determinism in algorithms working with the model (e.g. sync)
            copy(references = newReferences.sortedBy { it.first })
        }
    }

    private fun IRoleReference.chooseIdOrName(useRoleIds: Boolean) = if (useRoleIds) getIdOrName() else getNameOrId()
    private fun IChildLinkReference.chooseIdOrNameOrNull(useRoleIds: Boolean) = if (useRoleIds) getIdOrNameOrNull() else getNameOrIdOrNull()

    fun withChildRemoved(childId: NodeId): IStream.One<NodeObjectData<NodeId>> {
        return getChildIds().filter { !deserializer!!.nodeIdTypeConfig.equal(it, childId) }.toList().map { newChildren ->
            withChildren(newChildren)
        }
    }

    fun withChildren(newChildren: List<NodeId>) = copy(children = deserializer!!.largeListConfig.createList(newChildren))

    override fun objectDiff(self: Object<*>, oldObject: Object<*>?): IStream.Many<Object<*>> {
        return self.getDescendantsAndSelf()
    }

    class Deserializer<NodeId>(
        val graph: IObjectGraph,
        val nodeIdTypeConfig: IDataTypeConfiguration<NodeId>,
        val treeId: TreeId,
    ) : IObjectDeserializer<NodeObjectData<NodeId>> {
        val referenceTypeConfig = LegacyNodeReferenceDataTypeConfig(treeId)
        val largeListConfig = LargeListConfig(graph, nodeIdTypeConfig)
        val serialFormat = SplitJoinFormat(
            SerializersModule {
                contextual(INodeReference::class, referenceTypeConfig.asKSerializer())
                contextual(LargeList::class) { LargeListKSerializer(largeListConfig) }
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
                    role = value.getRoleInParentAsString(),
                    children = value.children ?: largeListConfig.createEmptyList(),
                    properties = value.properties.toMap(),
                    references = value.references.toMap(),
                )
            }

            override fun convertFromSerialized(value: LegacyCompatibleFormat<NodeId, INodeReference>): NodeObjectData<NodeId> {
                return NodeObjectData(
                    deserializer = this@Deserializer,
                    id = value.id,
                    concept = value.concept,
                    containment = decodeNullId(value.parent)?.let { it to value.role },
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
    @Contextual
    val children: LargeList<NodeId>,
    val properties: Map<String, String>,
    val references: Map<String, ReferenceType>,
)

fun IReadableNode.getTreeId(): TreeId {
    return when (this) {
        else -> throw IllegalArgumentException("Unknown node type: $this")
    }
}
