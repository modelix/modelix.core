package org.modelix.kotlin.utils

actual fun <K, V> createMemoryEfficientMap(): MutableMap<K, V> = HashMap()

actual fun <K, V> MutableMap<K, V>.toSynchronizedMap(): MutableMap<K, V> {
    // Because JS is single-threaded, no extra measures are needed.
    return this
}
