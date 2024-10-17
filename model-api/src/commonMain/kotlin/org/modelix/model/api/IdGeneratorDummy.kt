package org.modelix.model.api

class IdGeneratorDummy : IIdGenerator {
    override fun generate(): Long {
        throw UnsupportedOperationException("Unexpected generation of IDs")
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other == null || this::class != other::class) return false
        return true
    }

    override fun hashCode(): Int {
        return this::class.hashCode()
    }
}
