package org.modelix.datastructures.btree

import saschpe.kase64.base64UrlDecoded
import saschpe.kase64.base64UrlEncoded

data class BTreeConfig<K, V>(
    val minEntries: Int,
    val keyComparator: Comparator<K>,
    /**
     * Has to escape special characters that are not part of Base64Url.
     */
    val keySerializer: (K) -> String,
    val keyDeserializer: (String) -> K,
    /**
     * Has to escape special characters that are not part of Base64Url.
     */
    val valueSerializer: (V) -> String,
    val valueDeserializer: (String) -> V,
) {
    val maxEntries = 2 * minEntries
    val nodeDeserializer = BTreeNode.Deserializer(this)

    companion object {
        fun builder() = BTreeConfigBuilder<Nothing, Nothing>()
    }
}

class BTreeConfigBuilder<K, V> {
    private var minEntries: Int = 8
    private var keyComparator: Comparator<K>? = null
    private var keySerializer: ((K) -> String)? = null
    private var keyDeserializer: ((String) -> K)? = null
    private var valueSerializer: ((V) -> String)? = null
    private var valueDeserializer: ((String) -> V)? = null

    fun minEntries(value: Int) = also { minEntries = value }

    fun maxEntries(value: Int) = also {
        require(value % 2 == 0) { "Has to be a multiple of 2: $value" }
        minEntries(value / 2)
    }

    fun compareKeysBy(selector: (K) -> Comparable<*>?) = keyComparator(compareBy(selector))
    fun keyComparator(value: Comparator<K>) = also { keyComparator = value }
    fun keySerializer(value: (K) -> String) = also { keySerializer = value }
    fun valueSerializer(value: (V) -> String) = also { valueSerializer = value }
    fun keyDeserializer(value: (String) -> K) = also { keyDeserializer = value }
    fun valueDeserializer(value: (String) -> V) = also { valueDeserializer = value }

    fun stringKeys(): BTreeConfigBuilder<String, V> {
        return (this as BTreeConfigBuilder<String, V>)
            .compareKeysBy { it }
            .keySerializer { it.base64UrlEncoded }
            .keyDeserializer { it.base64UrlDecoded }
    }

    fun longValues(): BTreeConfigBuilder<K, Long> {
        return (this as BTreeConfigBuilder<K, Long>)
            .valueSerializer { it.toUInt().toString(16) }
            .valueDeserializer { it.toULong(16).toLong() }
    }

    fun stringValues(): BTreeConfigBuilder<K, String> {
        return (this as BTreeConfigBuilder<K, String>)
            .valueSerializer { it.base64UrlEncoded }
            .valueDeserializer { it.base64UrlDecoded }
    }

    fun build() = BTreeConfig(
        minEntries = minEntries,
        keyComparator = checkNotNull(keyComparator) { "keyComparator not specified" },
        keySerializer = checkNotNull(keySerializer) { "keySerializer not specified" },
        keyDeserializer = checkNotNull(keyDeserializer) { "keyDeserializer not specified" },
        valueSerializer = checkNotNull(valueSerializer) { "valueSerializer not specified" },
        valueDeserializer = checkNotNull(valueDeserializer) { "valueDeserializer not specified" },
    )
}