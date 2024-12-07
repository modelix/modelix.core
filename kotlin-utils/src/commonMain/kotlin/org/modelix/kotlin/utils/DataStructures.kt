package org.modelix.kotlin.utils

/**
 * Creates a mutable map with less memory overhead.
 * This is an internal API.
 *
 * Built-in maps like [HashMap] are not the not very memory efficient.
 * A common issue is that entry objects for every item in the table are created.
 * Java implementation is optimized to not create entry objects by using a map implementation from another library.
 * The JS implementation is not optimized yet because we did not invest time in finding a suitable library.
 *
 * We did not look into performance implications for storing and retrieving data.
 * Therefore, the memory efficient maps are used sparingly for only the very big maps.
 */
expect fun <K, V> createMemoryEfficientMap(): MutableMap<K, V>

expect fun <K, V> MutableMap<K, V>.toSynchronizedMap(): MutableMap<K, V>
