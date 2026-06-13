package org.modelix.streams2

/**
 * A batchable data source. The whole point of this stream implementation is to coalesce many individual
 * [fetch][IStream.Companion.fetch] requests against the same source into a single bulk call.
 *
 * Implementations must return a value for every requested key. A key that is absent from the returned map is
 * treated as a missing value and surfaces as a [NoSuchElementException] in the stream that requested it.
 */
interface IBulkExecutor<K, V> {
    fun execute(keys: List<K>): Map<K, V>
}
