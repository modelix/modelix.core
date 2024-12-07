package org.modelix.model.client

import org.modelix.model.api.IIdGenerator

expect class IdGenerator private constructor(clientId: Int) : IIdGenerator {
    override fun generate(): Long
    fun generate(quantity: Int): LongRange
    companion object {
        fun getInstance(clientId: Int): IdGenerator
        fun newInstance(clientId: Int): IdGenerator
    }
}
