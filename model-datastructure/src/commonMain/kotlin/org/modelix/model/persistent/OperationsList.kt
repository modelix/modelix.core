package org.modelix.model.persistent

import org.modelix.datastructures.objects.IObjectData
import org.modelix.datastructures.objects.IObjectDeserializer
import org.modelix.datastructures.objects.IObjectReferenceFactory
import org.modelix.datastructures.objects.Object
import org.modelix.datastructures.objects.ObjectReference
import org.modelix.datastructures.objects.getDescendantsAndSelf
import org.modelix.datastructures.objects.getHashString
import org.modelix.model.operations.IOperation
import org.modelix.streams.IStream

abstract class OperationsList() : IObjectData {
    companion object : IObjectDeserializer<OperationsList> {
        val DESERIALIZER: IObjectDeserializer<OperationsList> = this
        private const val MAX_LIST_SIZE = 20
        private const val LARGE_LIST_PREFIX = "OL" + Separators.LEVEL1

        override fun deserialize(input: String, referenceFactory: IObjectReferenceFactory): OperationsList {
            val data = if (input.startsWith(LARGE_LIST_PREFIX)) {
                val subLists = input.substring(LARGE_LIST_PREFIX.length)
                    .split(Separators.LEVEL2)
                    .map { referenceFactory(it, DESERIALIZER) }
                    .toTypedArray()
                LargeOperationsList(subLists)
            } else {
                SmallOperationsList(
                    input.split(Separators.LEVEL2)
                        .filter { it.isNotEmpty() }
                        .map { OperationSerializer.INSTANCE.deserialize(it, referenceFactory) }
                        .toTypedArray(),
                )
            }
            return data
        }

        fun of(operations: List<IOperation>, referenceFactory: IObjectReferenceFactory): OperationsList {
            return if (operations.size <= MAX_LIST_SIZE) {
                SmallOperationsList(operations.toTypedArray())
            } else {
                // split the operations into at most MAX_LIST_SIZE sub lists
                val sublistSizes = ((operations.size + MAX_LIST_SIZE - 1) / MAX_LIST_SIZE).coerceAtLeast(MAX_LIST_SIZE)
                LargeOperationsList(operations.chunked(sublistSizes) { referenceFactory(of(it, referenceFactory)) }.toTypedArray())
            }
        }
    }

    abstract fun getOperations(): IStream.Many<IOperation>

    override fun objectDiff(self: Object<*>, oldObject: Object<*>?): IStream.Many<Object<*>> {
        return self.getDescendantsAndSelf()
    }
}

class LargeOperationsList(val subLists: Array<out ObjectReference<OperationsList>>) : OperationsList() {
    override fun serialize(): String {
        return "OL" + Separators.LEVEL1 + subLists.joinToString(Separators.LEVEL2) { it.getHashString() }
    }

    override fun getDeserializer(): IObjectDeserializer<OperationsList> = DESERIALIZER

    override fun getContainmentReferences(): List<ObjectReference<IObjectData>> {
        return subLists.toList()
    }

    override fun getOperations(): IStream.Many<IOperation> {
        return IStream.many(subLists).flatMap {
            it.resolveData().flatMap { it.getOperations() }
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

    override fun getDeserializer(): IObjectDeserializer<OperationsList> = DESERIALIZER

    override fun getContainmentReferences(): List<ObjectReference<IObjectData>> {
        return operations.map { it.getObjectReferences() }.flatten()
    }

    override fun getOperations(): IStream.Many<IOperation> {
        return IStream.many(operations)
    }
}
