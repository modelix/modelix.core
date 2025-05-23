package org.modelix.model.persistent

import org.modelix.datastructures.objects.IObjectReferenceFactory
import org.modelix.datastructures.objects.getHashString
import org.modelix.kotlin.utils.urlDecode
import org.modelix.kotlin.utils.urlEncode
import org.modelix.model.api.ConceptReference
import org.modelix.model.api.IChildLinkReference
import org.modelix.model.api.IConceptReference
import org.modelix.model.api.INodeReference
import org.modelix.model.api.IPropertyReference
import org.modelix.model.api.IReferenceLinkReference
import org.modelix.model.api.LocalPNodeReference
import org.modelix.model.api.NodeReference
import org.modelix.model.api.PNodeReference
import org.modelix.model.api.meta.NullConcept
import org.modelix.model.operations.AddNewChildOp
import org.modelix.model.operations.AddNewChildSubtreeOp
import org.modelix.model.operations.AddNewChildrenOp
import org.modelix.model.operations.BulkUpdateOp
import org.modelix.model.operations.DeleteNodeOp
import org.modelix.model.operations.IOperation
import org.modelix.model.operations.MoveNodeOp
import org.modelix.model.operations.NoOp
import org.modelix.model.operations.PositionInRole
import org.modelix.model.operations.RevertToOp
import org.modelix.model.operations.SetConceptOp
import org.modelix.model.operations.SetPropertyOp
import org.modelix.model.operations.SetReferenceOp
import org.modelix.model.operations.UndoOp
import org.modelix.model.persistent.SerializationUtil.escape
import org.modelix.model.persistent.SerializationUtil.longFromHex
import org.modelix.model.persistent.SerializationUtil.longToHex
import org.modelix.model.persistent.SerializationUtil.unescape
import kotlin.reflect.KClass

private val localNodeIdPattern = Regex("[a-fA-F0-9]+")

@Deprecated("uses invalid '/' separator that is already reserved for top level objects")
private val globalNodeIdPattern = Regex("[a-fA-F0-9]+/.+")

class OperationSerializer private constructor() {
    companion object {
        val INSTANCE = OperationSerializer()
        private const val SEPARATOR = Separators.OP_PARTS
        fun serializeConcept(concept: IConceptReference?): String {
            return escape(concept?.takeIf { it != NullConcept.getReference() }?.serialize())
        }

        fun deserializeConcept(serialized: String?): ConceptReference {
            return unescape(serialized)?.let { ConceptReference(it) } ?: NullConcept.getReference()
        }

        fun deserializeNode(serialized: String?): INodeReference {
            return NodeReference(serialized?.urlDecode()!!)
        }

        fun serializeNode(ref: INodeReference) = ref.serialize().urlEncode()

        fun serializeReference(obj: INodeReference?): String {
            return when (obj) {
                null -> ""
                is LocalPNodeReference -> longToHex(obj.id)
                // This was previously using the '/' separator, that is already used for the top level objects
                // and would fail to deserialize.
                // is PNodeReference -> {
                //     "${longToHex(obj.id)}/${escape(obj.branchId)}"
                // }
                else -> escape(obj.serialize())
            }
        }

        fun deserializeReference(serialized: String?): INodeReference? {
            return when {
                serialized.isNullOrEmpty() -> null
                serialized.matches(localNodeIdPattern) -> LocalPNodeReference(longFromHex(serialized))
                serialized.matches(globalNodeIdPattern) -> {
                    // This is how PNodeReference was serialized before, but the deserialization of the CPVersion
                    // should fail with this duplicate usage of the '/' separator.
                    // This branch should not be reachable and can be removed if we are absolutely sure about that.
                    val parts = serialized.split('/', limit = 2)
                    PNodeReference(longFromHex(parts[0]), unescape(parts[1])!!)
                }
                else -> unescape(serialized)?.let {
                    PNodeReference.tryDeserialize(serialized) ?: NodeReference(it)
                }
            }
        }

        init {
            INSTANCE.registerSerializer(
                AddNewChildOp::class,
                object : Serializer<AddNewChildOp> {
                    override fun serialize(op: AddNewChildOp): String {
                        return serializeNode(op.position.nodeId) +
                            SEPARATOR +
                            escape(op.position.role.stringForLegacyApi()) +
                            SEPARATOR +
                            op.position.index +
                            SEPARATOR +
                            serializeNode(op.childId) +
                            SEPARATOR +
                            serializeConcept(op.concept)
                    }

                    override fun deserialize(serialized: String, referenceFactory: IObjectReferenceFactory): AddNewChildOp {
                        val parts = serialized.split(SEPARATOR).toTypedArray()
                        return AddNewChildOp(
                            PositionInRole(
                                deserializeNode(parts[0]),
                                IChildLinkReference.fromLegacyApi(unescape(parts[1])),
                                parts[2].toInt(),
                            ),
                            deserializeNode(parts[3]),
                            deserializeConcept(parts[4]),
                        )
                    }
                },
            )
            INSTANCE.registerSerializer(
                AddNewChildrenOp::class,
                object : Serializer<AddNewChildrenOp> {
                    override fun serialize(op: AddNewChildrenOp): String {
                        val ids = op.childIdsAndConcepts.joinToString(Separators.LEVEL4) { serializeNode(it.first) }
                        val concepts = op.childIdsAndConcepts.joinToString(Separators.LEVEL4) { serializeConcept(it.second) }
                        return serializeNode(op.position.nodeId) +
                            SEPARATOR +
                            escape(op.position.role.stringForLegacyApi()) +
                            SEPARATOR +
                            op.position.index +
                            SEPARATOR +
                            ids +
                            SEPARATOR +
                            concepts
                    }

                    override fun deserialize(serialized: String, referenceFactory: IObjectReferenceFactory): AddNewChildrenOp {
                        val parts = serialized.split(SEPARATOR).toTypedArray()
                        val ids = parts[3].split(Separators.LEVEL4).filter { it.isNotEmpty() }.map { deserializeNode(it) }
                        val concepts = parts[4].split(Separators.LEVEL4).map { deserializeConcept(it) }.toTypedArray()
                        return AddNewChildrenOp(
                            PositionInRole(
                                deserializeNode(parts[0]),
                                IChildLinkReference.fromLegacyApi(unescape(parts[1])),
                                parts[2].toInt(),
                            ),
                            ids.zip(concepts),
                        )
                    }
                },
            )
            INSTANCE.registerSerializer(
                AddNewChildSubtreeOp::class,
                object : Serializer<AddNewChildSubtreeOp> {
                    override fun serialize(op: AddNewChildSubtreeOp): String {
                        return serializeNode(op.position.nodeId) +
                            SEPARATOR +
                            escape(op.position.role.stringForLegacyApi()) +
                            SEPARATOR +
                            op.position.index +
                            SEPARATOR +
                            serializeNode(op.childId) +
                            SEPARATOR +
                            serializeConcept(op.concept) +
                            SEPARATOR +
                            op.resultTreeHash.getHash()
                    }

                    override fun deserialize(serialized: String, referenceFactory: IObjectReferenceFactory): AddNewChildSubtreeOp {
                        val parts = serialized.split(SEPARATOR).toTypedArray()
                        return AddNewChildSubtreeOp(
                            referenceFactory.fromHashString(parts[5], CPTree.DESERIALIZER),
                            PositionInRole(
                                deserializeNode(parts[0]),
                                IChildLinkReference.fromLegacyApi(unescape(parts[1])),
                                parts[2].toInt(),
                            ),
                            deserializeNode(parts[3]),
                            deserializeConcept(parts[4]),
                        )
                    }
                },
            )
            INSTANCE.registerSerializer(
                BulkUpdateOp::class,
                object : Serializer<BulkUpdateOp> {
                    override fun serialize(op: BulkUpdateOp): String {
                        return serializeNode(op.subtreeRootId) + SEPARATOR + op.resultTreeHash.getHash()
                    }

                    override fun deserialize(serialized: String, referenceFactory: IObjectReferenceFactory): BulkUpdateOp {
                        val parts = serialized.split(SEPARATOR).toTypedArray()
                        return BulkUpdateOp(referenceFactory(parts[1], CPTree.DESERIALIZER), deserializeNode(parts[0]))
                    }
                },
            )
            INSTANCE.registerSerializer(
                DeleteNodeOp::class,
                object : Serializer<DeleteNodeOp> {
                    override fun serialize(op: DeleteNodeOp): String {
                        return serializeNode(op.childId)
                    }

                    override fun deserialize(serialized: String, referenceFactory: IObjectReferenceFactory): DeleteNodeOp {
                        val parts = serialized.split(SEPARATOR)
                        return if (parts.size == 1) {
                            DeleteNodeOp(deserializeNode(parts[0]))
                        } else {
                            DeleteNodeOp(deserializeNode(parts[3]))
                        }
                    }
                },
            )
            INSTANCE.registerSerializer(
                MoveNodeOp::class,
                object : Serializer<MoveNodeOp> {
                    override fun serialize(op: MoveNodeOp): String {
                        return serializeNode(op.childId) + SEPARATOR +
                            serializeNode(op.targetPosition.nodeId) + SEPARATOR +
                            escape(op.targetPosition.role.stringForLegacyApi()) + SEPARATOR +
                            op.targetPosition.index
                    }

                    override fun deserialize(serialized: String, referenceFactory: IObjectReferenceFactory): MoveNodeOp {
                        val parts = serialized.split(SEPARATOR)
                        return if (parts.size == 4) {
                            MoveNodeOp(
                                deserializeNode(parts[0]),
                                PositionInRole(deserializeNode(parts[1]), IChildLinkReference.fromLegacyApi(unescape(parts[2])), parts[3].toInt()),
                            )
                        } else {
                            MoveNodeOp(
                                deserializeNode(parts[0]),
                                PositionInRole(deserializeNode(parts[4]), IChildLinkReference.fromLegacyApi(unescape(parts[5])), parts[6].toInt()),
                            )
                        }
                    }
                },
            )
            INSTANCE.registerSerializer(
                NoOp::class,
                object : Serializer<NoOp> {
                    override fun serialize(op: NoOp): String {
                        return ""
                    }

                    override fun deserialize(serialized: String, referenceFactory: IObjectReferenceFactory): NoOp {
                        return NoOp()
                    }
                },
            )
            INSTANCE.registerSerializer(
                SetPropertyOp::class,
                object : Serializer<SetPropertyOp> {
                    override fun serialize(op: SetPropertyOp): String {
                        return serializeNode(op.nodeId) + SEPARATOR + escape(op.role.stringForLegacyApi()) + SEPARATOR + escape(op.value)
                    }

                    override fun deserialize(serialized: String, referenceFactory: IObjectReferenceFactory): SetPropertyOp {
                        val parts = serialized.split(SEPARATOR).toTypedArray()
                        return SetPropertyOp(deserializeNode(parts[0]), IPropertyReference.fromString(unescape(parts[1])!!), unescape(parts[2]))
                    }
                },
            )
            INSTANCE.registerSerializer(
                SetReferenceOp::class,
                object : Serializer<SetReferenceOp> {
                    override fun serialize(op: SetReferenceOp): String {
                        return serializeNode(op.sourceId) + SEPARATOR + escape(op.role.stringForLegacyApi()) + SEPARATOR + serializeReference(op.target)
                    }

                    override fun deserialize(serialized: String, referenceFactory: IObjectReferenceFactory): SetReferenceOp {
                        val parts = serialized.split(SEPARATOR).toTypedArray()
                        return SetReferenceOp(deserializeNode(parts[0]), IReferenceLinkReference.fromString(unescape(parts[1])!!), deserializeReference(parts[2]))
                    }
                },
            )
            INSTANCE.registerSerializer(
                SetConceptOp::class,
                object : Serializer<SetConceptOp> {
                    override fun serialize(op: SetConceptOp): String {
                        return serializeNode(op.nodeId) + SEPARATOR + serializeConcept(op.concept)
                    }

                    override fun deserialize(serialized: String, referenceFactory: IObjectReferenceFactory): SetConceptOp {
                        val parts = serialized.split(SEPARATOR)
                        return SetConceptOp(nodeId = deserializeNode(parts[0]), concept = deserializeConcept(parts[1]))
                    }
                },
            )
            INSTANCE.registerSerializer(
                UndoOp::class,
                object : Serializer<UndoOp> {
                    override fun serialize(op: UndoOp): String {
                        return op.versionHash.getHashString()
                    }

                    override fun deserialize(serialized: String, referenceFactory: IObjectReferenceFactory): UndoOp {
                        return UndoOp(referenceFactory(serialized, CPVersion.DESERIALIZER))
                    }
                },
            )
            INSTANCE.registerSerializer(
                RevertToOp::class,
                object : Serializer<RevertToOp> {
                    override fun serialize(op: RevertToOp): String {
                        return op.latestKnownVersionRef.getHashString() +
                            SEPARATOR +
                            op.versionToRevertToRef.getHashString()
                    }

                    override fun deserialize(serialized: String, referenceFactory: IObjectReferenceFactory): RevertToOp {
                        val parts = serialized.split(SEPARATOR).toTypedArray()
                        return RevertToOp(
                            referenceFactory(parts[0], CPVersion.DESERIALIZER),
                            referenceFactory(parts[1], CPVersion.DESERIALIZER),
                        )
                    }
                },
            )
        }
    }

    private val serializers: MutableMap<KClass<out IOperation>, Serializer<*>> = HashMap()
    private val deserializers: MutableMap<String, Serializer<*>> = HashMap()
    fun <T : IOperation> registerSerializer(type: KClass<T>, serializer: Serializer<T>) {
        serializers[type] = serializer
        deserializers[type.simpleName!!] = serializer
    }

    fun serialize(op: IOperation): String {
        val serializer: Serializer<*> = serializers[op::class]
            ?: throw RuntimeException("Unknown operation type: " + op::class.simpleName)
        return op::class.simpleName + SEPARATOR + serializer.genericSerialize(op)
    }

    fun deserialize(serialized: String, referenceFactory: IObjectReferenceFactory): IOperation {
        val parts = serialized.split(SEPARATOR, limit = 2).toTypedArray()
        val deserializer = deserializers[parts[0]]
            ?: throw RuntimeException("Unknown operation type: " + parts[0])
        return deserializer.deserialize(parts[1], referenceFactory)!!
    }

    interface Serializer<E : IOperation?> {
        fun genericSerialize(op: Any): String {
            val p = op as? E
            if (p == null) {
                throw IllegalArgumentException()
            } else {
                return serialize(p)
            }
        }
        fun serialize(op: E): String
        fun deserialize(serialized: String, referenceFactory: IObjectReferenceFactory): E
    }
}
