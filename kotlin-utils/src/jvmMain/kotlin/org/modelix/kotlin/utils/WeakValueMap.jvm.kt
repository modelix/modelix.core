package org.modelix.kotlin.utils

import com.google.common.collect.MapMaker

actual class WeakValueMap<K : Any, V : Any> {
    private val map = MapMaker().weakValues().makeMap<K, V>()

    actual fun get(key: K): V? {
        return map[key]
    }

    actual fun put(key: K, value: V) {
        map.put(key, value)
    }

    actual fun toMap(): Map<K, V> {
        return map.toMap()
    }
}
