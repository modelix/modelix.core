package org.modelix.model.client

import org.modelix.model.api.IIdGenerator

actual class IdGenerator internal actual constructor(clientId: Int) : IIdGenerator {
    private var idSequence: Long
    private val clientId: Long = clientId.toLong()
    actual override fun generate(): Long {
        return generate(1).first
    }
    actual fun generate(quantity: Int): LongRange {
        require(quantity >= 1)
        idSequence += quantity.toLong()
        val lastId = idSequence
        if (lastId ushr 32 != clientId) {
            throw RuntimeException("End of ID range")
        }
        val firstId = lastId - quantity + 1
        return LongRange(firstId, lastId)
    }

    init {
        idSequence = this.clientId shl 32
    }

    actual companion object {
        private val instances: MutableMap<Int, IdGenerator> = HashMap()
        actual fun getInstance(clientId: Int): IdGenerator {
            return instances.getOrPut(clientId) { IdGenerator(clientId) }
        }
        actual fun newInstance(clientId: Int): IdGenerator = IdGenerator(clientId)
    }
}
