package org.modelix.model.persistent

import org.modelix.model.objects.IObjectData
import org.modelix.model.objects.IObjectLoader
import org.modelix.model.objects.Object
import org.modelix.model.objects.ObjectReference
import org.modelix.model.objects.getDescendantsAndSelf
import org.modelix.streams.IStream
import org.modelix.streams.plus
import kotlin.jvm.JvmStatic

class CPTree(
    val id: String,
    var idToHash: ObjectReference<CPHamtNode>,
    val usesRoleIds: Boolean,
) : IObjectData {
    override fun serialize(): String {
        // TODO version bump required for the new operations BulkUpdateOp and AddNewChildrenOp
        val pv = if (usesRoleIds) PERSISTENCE_VERSION else NAMED_BASED_PERSISTENCE_VERSION
        return "$id/$pv/${idToHash.getHash()}"
    }

    override fun getDeserializer(): (String) -> CPTree = DESERIALIZER

    override fun getContainmentReferences(): List<ObjectReference<IObjectData>> = listOf(idToHash)

    override fun objectDiff(self: Object<*>, oldObject: Object<*>?, loader: IObjectLoader): IStream.Many<Object<*>> {
        return when (oldObject?.data) {
            is CPTree -> IStream.of(self) + idToHash.diff(oldObject.data.idToHash, loader)
            else -> self.getDescendantsAndSelf(loader)
        }
    }

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
            val data = CPTree(treeId, ObjectReference(parts[2], CPHamtNode.DESERIALIZER), usesRoleIds)
            return data
        }
    }
}
