package org.modelix.model.persistent

import org.modelix.model.lazy.KVEntryReference
import kotlin.jvm.JvmStatic

class CPTree(
    val id: String,
    var idToHash: KVEntryReference<CPHamtNode>,
    val usesRoleIds: Boolean,
) : IKVValue {
    override var isWritten: Boolean = false

    override fun serialize(): String {
        // TODO version bump required for the new operations BulkUpdateOp and AddNewChildrenOp
        val pv = if (usesRoleIds) PERSISTENCE_VERSION else NAMED_BASED_PERSISTENCE_VERSION
        return "$id/$pv/${idToHash.getHash()}"
    }

    override val hash: String by lazy(LazyThreadSafetyMode.PUBLICATION) { HashUtil.sha256(serialize()) }

    override fun getDeserializer(): (String) -> IKVValue = DESERIALIZER

    override fun getReferencedEntries(): List<KVEntryReference<IKVValue>> = listOf(idToHash)

    companion object {
        /**
         * Since version 3 the UID of concept members is stored instead of the name
         */
        val PERSISTENCE_VERSION: Int = 3
        val NAMED_BASED_PERSISTENCE_VERSION: Int = 2
        val DESERIALIZER: (String) -> CPTree = { deserialize(it) }

        @JvmStatic
        fun deserialize(input: String): CPTree {
            val parts = input.split(Separators.LEVEL1)
            val treeId = parts[0]
            val persistenceVersion = parts[1].toInt()
            if (persistenceVersion != PERSISTENCE_VERSION && persistenceVersion != NAMED_BASED_PERSISTENCE_VERSION) {
                throw RuntimeException(
                    "Tree $treeId has persistence version $persistenceVersion, " +
                        "but only version $PERSISTENCE_VERSION is supported",
                )
            }
            val usesRoleIds = persistenceVersion == PERSISTENCE_VERSION
            val data = CPTree(treeId, KVEntryReference(parts[2], CPHamtNode.DESERIALIZER), usesRoleIds)
            data.isWritten = true
            return data
        }
    }
}
