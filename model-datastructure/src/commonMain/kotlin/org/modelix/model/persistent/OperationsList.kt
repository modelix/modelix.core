package org.modelix.model.persistent

import org.modelix.model.objects.IObjectData
import org.modelix.model.objects.IObjectLoader
import org.modelix.model.objects.ObjectReference
import org.modelix.model.objects.getHashString
import org.modelix.model.operations.IOperation
import org.modelix.streams.IStream

abstract class OperationsList() : IObjectData {
    companion object {
        val DESERIALIZER: (String) -> OperationsList = { deserialize(it) }
        private const val MAX_LIST_SIZE = 20
        private const val LARGE_LIST_PREFIX = "OL" + Separators.LEVEL1
        fun deserialize(input: String): OperationsList {
            val data = if (input.startsWith(LARGE_LIST_PREFIX)) {
                val subLists = input.substring(LARGE_LIST_PREFIX.length)
                    .split(Separators.LEVEL2)
                    .map { ObjectReference(it, DESERIALIZER) }
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
            return data
        }

        fun of(operations: List<IOperation>): OperationsList {
            return if (operations.size <= MAX_LIST_SIZE) {
                SmallOperationsList(operations.toTypedArray())
            } else {
                // split the operations into at most MAX_LIST_SIZE sub lists
                val sublistSizes = ((operations.size + MAX_LIST_SIZE - 1) / MAX_LIST_SIZE).coerceAtLeast(MAX_LIST_SIZE)
                LargeOperationsList(operations.chunked(sublistSizes) { ObjectReference(of(it)) }.toTypedArray())
            }
        }
    }

    abstract fun getOperations(loader: IObjectLoader): IStream.Many<IOperation>
}

class LargeOperationsList(val subLists: Array<out ObjectReference<OperationsList>>) : OperationsList() {
    override fun serialize(): String {
        return "OL" + Separators.LEVEL1 + subLists.joinToString(Separators.LEVEL2) { it.getHashString() }
    }

    override fun getDeserializer(): (String) -> OperationsList = DESERIALIZER

    override fun getContainmentReferences(): List<ObjectReference<IObjectData>> {
        return subLists.toList()
    }

    override fun getOperations(loader: IObjectLoader): IStream.Many<IOperation> {
        return IStream.many(subLists).flatMap {
            it.requestData(loader).flatMap { it.getOperations(loader) }
        }
    }
}

class SmallOperationsList(val operations: Array<out IOperation>) : OperationsList() {
    override fun serialize(): String {
        return if (operations.isEmpty()) {
            ""
        } else {
            operations
                .joinToString(Separators.LEVEL2) { OperationSerializer.INSTANCE.serialize(it) }
        }
    }

    override fun getDeserializer(): (String) -> OperationsList = DESERIALIZER

    override fun getContainmentReferences(): List<ObjectReference<IObjectData>> {
        return operations.map { it.getObjectReferences() }.flatten()
    }

    override fun getOperations(loader: IObjectLoader): IStream.Many<IOperation> {
        return IStream.many(operations)
    }
}
