package org.modelix.model.persistent

import org.modelix.datastructures.IPersistentMapRootData
import org.modelix.datastructures.autoResolveValues
import org.modelix.datastructures.createMapInstance
import org.modelix.datastructures.hamt.HamtNode
import org.modelix.datastructures.model.IGenericModelTree
import org.modelix.datastructures.model.NodeObjectData
import org.modelix.datastructures.model.NodeReferenceDataTypeConfig
import org.modelix.datastructures.model.asModelTree
import org.modelix.datastructures.model.withIdTranslation
import org.modelix.datastructures.objects.IObjectData
import org.modelix.datastructures.objects.IObjectDeserializer
import org.modelix.datastructures.objects.IObjectGraph
import org.modelix.datastructures.objects.IObjectReferenceFactory
import org.modelix.datastructures.objects.LongDataTypeConfiguration
import org.modelix.datastructures.objects.Object
import org.modelix.datastructures.objects.ObjectReference
import org.modelix.datastructures.objects.ObjectReferenceDataTypeConfiguration
import org.modelix.datastructures.objects.getDescendantsAndSelf
import org.modelix.datastructures.patricia.PatriciaNode
import org.modelix.datastructures.patricia.PatriciaTrieConfig
import org.modelix.model.TreeId
import org.modelix.model.api.INodeReference
import org.modelix.model.api.ITree
import org.modelix.streams.IStream
import org.modelix.streams.flatten
import org.modelix.streams.plus

typealias ModelTreeRef = ObjectReference<CPTree>

data class CPTree(
    val id: TreeId,
    val int64Hamt: ObjectReference<IPersistentMapRootData<Long, ObjectReference<NodeObjectData<Long>>>>?,
    val trieWithNodeRefIds: ObjectReference<IPersistentMapRootData<INodeReference, ObjectReference<NodeObjectData<INodeReference>>>>?,
    val usesRoleIds: Boolean,
) : IObjectData {

    init {
        require(int64Hamt != null || trieWithNodeRefIds != null) { "No tree data provided" }
    }

    fun getTreeReference() = checkNotNull(trieWithNodeRefIds ?: int64Hamt) { "Not tree hash provided" }

    fun getLegacyModelTree(): IGenericModelTree<Long> {
        if (trieWithNodeRefIds != null) {
            return trieWithNodeRefIds.resolveNow().createMapInstance().autoResolveValues().asModelTree(id, usesRoleIds).withIdTranslation()
        }
        if (int64Hamt != null) {
            return int64Hamt.resolveNow().createMapInstance().autoResolveValues().asModelTree(id, usesRoleIds)
        }
        throw IllegalStateException("Doesn't contain any tree data")
    }

    fun getModelTree(): IGenericModelTree<INodeReference> {
        if (trieWithNodeRefIds != null) {
            return trieWithNodeRefIds.resolveNow().createMapInstance().autoResolveValues().asModelTree(id, usesRoleIds)
        }
        if (int64Hamt != null) {
            return int64Hamt.resolveNow().createMapInstance().autoResolveValues().asModelTree(id, usesRoleIds).withIdTranslation()
        }
        throw IllegalStateException("Doesn't contain any tree data")
    }

    override fun serialize(): String {
        val pv = when {
            trieWithNodeRefIds != null -> STRING_IDS
            usesRoleIds -> INT64_WITH_ROLE_IDS
            else -> INT64_WITH_ROLE_NAMES
        }
        return "$id/$pv/${getTreeReference().getHash()}${if (usesRoleIds) "/i" else ""}"
    }

    override fun getDeserializer(): IObjectDeserializer<CPTree> = DESERIALIZER

    override fun getContainmentReferences(): List<ObjectReference<IObjectData>> {
        return listOf(getTreeReference())
    }

    override fun objectDiff(self: Object<*>, oldObject: Object<*>?): IStream.Many<Object<*>> {
        val oldData = oldObject?.data
        return when (oldData) {
            is CPTree -> {
                IStream.of(self) + getTreeReference().resolve().zipWith(oldData.getTreeReference().resolve()) { newTree, oldTree ->
                    newTree.objectDiff(oldTree)
                }.flatten()
            }
            else -> self.getDescendantsAndSelf()
        }
    }

    companion object : IObjectDeserializer<CPTree> {
        /**
         * Since version 4 the UID of concept members is stored instead of the name
         */
        val STRING_IDS: Int = 4

        /**
         * Tree datastructure was a hash array mapped trie with 64-bit integers
         */
        val INT64_WITH_ROLE_IDS: Int = 3

        val INT64_WITH_ROLE_NAMES: Int = 2
        val CURRENT_PERSISTENCE_VERSION = STRING_IDS
        val DESERIALIZER: IObjectDeserializer<CPTree> = this

        private fun patriciaDeserializer(graph: IObjectGraph, treeId: TreeId, usesRoleIds: Boolean): PatriciaNode.Deserializer<INodeReference, ObjectReference<NodeObjectData<INodeReference>>> {
            val nodeIdType = NodeReferenceDataTypeConfig()
            val config = PatriciaTrieConfig(
                graph = graph,
                keyConfig = nodeIdType,
                valueConfig = ObjectReferenceDataTypeConfiguration(graph, NodeObjectData.Deserializer(graph, nodeIdType, treeId)),
            )
            return PatriciaNode.Deserializer(config)
        }

        private fun hamtDeserializer(graph: IObjectGraph, treeId: TreeId, usesRoleIds: Boolean): IObjectDeserializer<HamtNode<Long, ObjectReference<NodeObjectData<Long>>>> {
            val nodeIdType = LongDataTypeConfiguration()
            return HamtNode.Config(
                graph = graph,
                keyConfig = nodeIdType,
                valueConfig = ObjectReferenceDataTypeConfiguration(
                    graph = graph,
                    deserializer = NodeObjectData.Deserializer(graph, nodeIdType, treeId),
                ),
            ).deserializer
        }

        override fun deserialize(input: String, referenceFactory: IObjectReferenceFactory): CPTree {
            val parts = input.split(Separators.LEVEL1)
            val treeId = TreeId.fromLegacyId(parts[0])
            val persistenceVersion = parts[1].toInt()
            val graph = referenceFactory as IObjectGraph
            return when (persistenceVersion) {
                STRING_IDS -> {
                    val usesRoleIds = when (val part = parts.getOrNull(3)) {
                        null -> false
                        "n" -> false
                        "i" -> true
                        else -> throw IllegalArgumentException("Unknown value `$part` in $input")
                    }
                    CPTree(
                        id = treeId,
                        int64Hamt = null,
                        trieWithNodeRefIds = referenceFactory.fromHashString(parts[2], patriciaDeserializer(graph, treeId, usesRoleIds = usesRoleIds)),
                        usesRoleIds = usesRoleIds,
                    )
                }
                INT64_WITH_ROLE_IDS -> {
                    CPTree(
                        id = treeId,
                        int64Hamt = referenceFactory.fromHashString(parts[2], hamtDeserializer(graph, treeId, usesRoleIds = true)),
                        trieWithNodeRefIds = null,
                        usesRoleIds = true,
                    )
                }
                INT64_WITH_ROLE_NAMES -> {
                    CPTree(
                        id = treeId,
                        int64Hamt = referenceFactory.fromHashString(parts[2], hamtDeserializer(graph, treeId, usesRoleIds = false)),
                        trieWithNodeRefIds = null,
                        usesRoleIds = false,
                    )
                }
                else -> {
                    throw RuntimeException(
                        "Tree $treeId has persistence version $persistenceVersion, " +
                            "but only version $CURRENT_PERSISTENCE_VERSION is supported",
                    )
                }
            }
        }
    }
}

fun ITree.getTreeObject() = (asObject() as Object<CPTree>)
