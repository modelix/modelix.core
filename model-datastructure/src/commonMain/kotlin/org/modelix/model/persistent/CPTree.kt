/*
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *  http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

package org.modelix.model.persistent

import org.modelix.model.lazy.IBulkQuery
import org.modelix.model.lazy.KVEntryReference
import org.modelix.model.lazy.wasDeserialized
import kotlin.jvm.JvmStatic

class CPTree(
    val id: String,
    var idToHash: KVEntryReference<CPHamtNode>,
    val usesRoleIds: Boolean,
) : IKVValue {
    override var ref: KVEntryReference<IKVValue>? = null

    override fun load(bulkQuery: IBulkQuery, reusableCandidate: KVEntryReference<*>?) {
        val reusableTree = reusableCandidate?.getValueIfLoaded() as? CPTree
        idToHash.load(bulkQuery, reusableTree?.idToHash)
    }

    override fun serialize(): String {
        // TODO version bump required for the new operations BulkUpdateOp and AddNewChildrenOp
        val pv = if (usesRoleIds) PERSISTENCE_VERSION else NAMED_BASED_PERSISTENCE_VERSION
        return "$id/$pv/${idToHash.getHash()}"
    }

    override val hash: String by lazy(LazyThreadSafetyMode.PUBLICATION) { HashUtil.sha256(serialize()) }

    override fun getDeserializer() = DESERIALIZER

    override fun getReferencedEntries(): List<KVEntryReference<IKVValue>> = listOf(idToHash)

    fun unloadSubtree(nodeId: Long) {
        val unloadResult = (idToHash.getValueIfLoaded() ?: return).unloadEntry(nodeId)
        if (unloadResult.unloadedValue != null) {
            for (childId in unloadResult.unloadedValue.childrenIdArray) {
                unloadSubtree(childId)
            }
        }
        if (unloadResult.wasLastLoadedEntry) {
            idToHash.unload()
        }
    }

    fun loadSubtree(nodeId: Long, bulkQuery: IBulkQuery) {
        idToHash.loadObject(bulkQuery).onSuccess {
            it?.loadEntry(nodeId, bulkQuery)?.onSuccess {
                if (it == null) return@onSuccess
                for (childId in it.childrenIdArray) {
                    loadSubtree(childId, bulkQuery)
                }
            }
        }
    }

    companion object {
        /**
         * Since version 3 the UID of concept members is stored instead of the name
         */
        val PERSISTENCE_VERSION: Int = 3
        val NAMED_BASED_PERSISTENCE_VERSION: Int = 2
        val DESERIALIZER = KVEntryReference.IDeserializer.create(CPTree::class, ::deserialize)

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
            val data = CPTree(treeId, KVEntryReference.fromHash(parts[2], CPHamtNode.DESERIALIZER), usesRoleIds)
            data.wasDeserialized()
            return data
        }
    }
}
