package org.modelix.model.api

/**
 * Generator for IDs.
 */
interface IIdGenerator {
    /**
     * Generates an id.
     *
     * @return generated id
     */
    fun generate(): Long
}
