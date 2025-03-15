package org.modelix.model.persistent

import org.modelix.model.async.IAsyncObjectStore
import org.modelix.model.lazy.KVEntryReference
import org.modelix.model.operations.IOperation
import org.modelix.streams.IStream

abstract class OperationsList() : IKVValue {
    companion object {
        val DESERIALIZER: (String) -> OperationsList = { deserialize(it) }
        private const val MAX_LIST_SIZE = 20
        private const val LARGE_LIST_PREFIX = "OL" + Separators.LEVEL1
        fun deserialize(input: String): OperationsList {
            val data = if (input.startsWith(LARGE_LIST_PREFIX)) {
                val subLists = input.substring(LARGE_LIST_PREFIX.length)
                    .split(Separators.LEVEL2)
                    .map { KVEntryReference(it, DESERIALIZER) }
                    .toTypedArray()
                LargeOperationsList(subLists)
            } else {
                SmallOperationsList(
                    input.split(Separators.LEVEL2)
                        .filter { it.isNotEmpty() }
                        .map { OperationSerializer.INSTANCE.deserialize(it) }
                        .toTypedArray(),
                )
            }
            data.isWritten = true
            return data
        }

        fun of(operations: List<IOperation>): OperationsList {
            return if (operations.size <= MAX_LIST_SIZE) {
                SmallOperationsList(operations.toTypedArray())
            } else {
                // split the operations into at most MAX_LIST_SIZE sub lists
                val sublistSizes = ((operations.size + MAX_LIST_SIZE - 1) / MAX_LIST_SIZE).coerceAtLeast(MAX_LIST_SIZE)
                LargeOperationsList(operations.chunked(sublistSizes) { KVEntryReference(of(it)) }.toTypedArray())
            }
        }
    }

    abstract fun getOperations(store: IAsyncObjectStore): IStream.Many<IOperation>
}

class LargeOperationsList(val subLists: Array<out KVEntryReference<OperationsList>>) : OperationsList() {
    override var isWritten: Boolean = false

    override fun serialize(): String {
        return "OL" + Separators.LEVEL1 + subLists.joinToString(Separators.LEVEL2) { it.getHash() }
    }

    override val hash: String by lazy(LazyThreadSafetyMode.PUBLICATION) { HashUtil.sha256(serialize()) }

    override fun getDeserializer(): (String) -> IKVValue = DESERIALIZER

    override fun getReferencedEntries(): List<KVEntryReference<IKVValue>> {
        return subLists.toList()
    }

    override fun getOperations(store: IAsyncObjectStore): IStream.Many<IOperation> {
        return IStream.many(subLists).flatMap {
            it.getValue(store).flatMap { it.getOperations(store) }
        }
    }
}

class SmallOperationsList(val operations: Array<out IOperation>) : OperationsList() {
    override var isWritten: Boolean = false

    override fun serialize(): String {
        return if (operations.isEmpty()) {
            ""
        } else {
            operations
                .joinToString(Separators.LEVEL2) { OperationSerializer.INSTANCE.serialize(it) }
        }
    }

    override val hash: String by lazy(LazyThreadSafetyMode.PUBLICATION) { HashUtil.sha256(serialize()) }

    override fun getDeserializer(): (String) -> IKVValue = DESERIALIZER

    override fun getReferencedEntries(): List<KVEntryReference<IKVValue>> {
        return operations.map { it.getReferencedEntries() }.flatten()
    }

    override fun getOperations(store: IAsyncObjectStore): IStream.Many<IOperation> {
        return IStream.many(operations)
    }
}
