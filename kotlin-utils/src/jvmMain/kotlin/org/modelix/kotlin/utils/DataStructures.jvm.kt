package org.modelix.kotlin.utils

import gnu.trove.map.hash.THashMap
import java.util.Collections

actual fun <K, V> createMemoryEfficientMap(): MutableMap<K, V> = THashMap()

actual fun <K, V> MutableMap<K, V>.toSynchronizedMap(): MutableMap<K, V> {
    return Collections.synchronizedMap(this)
}
