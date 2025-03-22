package org.modelix.model.persistent

import org.modelix.model.objects.IObjectData
import org.modelix.model.objects.IObjectDeserializer
import org.modelix.model.objects.IObjectReferenceFactory
import org.modelix.model.objects.Object
import org.modelix.model.objects.ObjectReference
import org.modelix.model.objects.getDescendantsAndSelf
import org.modelix.streams.IStream
import org.modelix.streams.plus

sealed interface ITreeData : IObjectData
sealed interface ITreeRelatedDeserializer<E : ITreeData> : IObjectDeserializer<E>

class CPTree(
    val id: String,
    var idToHash: ObjectReference<CPHamtNode>,
    val usesRoleIds: Boolean,
) : ITreeData {
    override fun serialize(): String {
        // TODO version bump required for the new operations BulkUpdateOp and AddNewChildrenOp
        val pv = if (usesRoleIds) PERSISTENCE_VERSION else NAMED_BASED_PERSISTENCE_VERSION
        return "$id/$pv/${idToHash.getHash()}"
    }

    override fun getDeserializer(): IObjectDeserializer<CPTree> = DESERIALIZER

    override fun getContainmentReferences(): List<ObjectReference<IObjectData>> = listOf(idToHash)

    override fun objectDiff(self: Object<*>, oldObject: Object<*>?): IStream.Many<Object<*>> {
        return when (oldObject?.data) {
            is CPTree -> IStream.of(self) + idToHash.diff(oldObject.data.idToHash)
            else -> self.getDescendantsAndSelf()
        }
    }

    companion object : ITreeRelatedDeserializer<CPTree> {
        /**
         * Since version 3 the UID of concept members is stored instead of the name
         */
        val PERSISTENCE_VERSION: Int = 3
        val NAMED_BASED_PERSISTENCE_VERSION: Int = 2
        val DESERIALIZER: IObjectDeserializer<CPTree> = this

        override fun deserialize(input: String, referenceFactory: IObjectReferenceFactory): CPTree {
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
            val data = CPTree(treeId, referenceFactory(parts[2], CPHamtNode), usesRoleIds)
            return data
        }
    }
}
