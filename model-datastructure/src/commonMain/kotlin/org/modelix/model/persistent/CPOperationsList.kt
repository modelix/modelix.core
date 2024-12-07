package org.modelix.model.persistent

import org.modelix.model.lazy.KVEntryReference
import org.modelix.model.operations.IOperation

class CPOperationsList(val operations: Array<IOperation>) : IKVValue {
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

    companion object {
        val DESERIALIZER: (String) -> CPOperationsList = { deserialize(it) }

        fun deserialize(input: String): CPOperationsList {
            val data = CPOperationsList(
                input.split(Separators.LEVEL2)
                    .filter { it.isNotEmpty() }
                    .map { OperationSerializer.INSTANCE.deserialize(it) }
                    .toTypedArray(),
            )
            data.isWritten = true
            return data
        }
    }
}
