package org.modelix.model.operations

import org.modelix.model.api.ITree
import org.modelix.model.api.IWriteTransaction
import org.modelix.model.lazy.IDeserializingKeyValueStore
import org.modelix.model.lazy.KVEntryReference
import org.modelix.model.persistent.IKVValue

sealed interface IOperation {
    fun apply(transaction: IWriteTransaction, store: IDeserializingKeyValueStore): IAppliedOperation
    fun captureIntend(tree: ITree, store: IDeserializingKeyValueStore): IOperationIntend
    fun getReferencedEntries(): List<KVEntryReference<IKVValue>>
}
