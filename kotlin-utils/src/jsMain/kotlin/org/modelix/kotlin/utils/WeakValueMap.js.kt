package org.modelix.kotlin.utils

import js.memory.FinalizationRegistry
import js.memory.WeakRef

actual class WeakValueMap<K : Any, V : Any> {
    private val map = LinkedHashMap<K, WeakRef<V>>()
    private val registry = FinalizationRegistry<K> { map.remove(it) }

    actual fun get(key: K): V? {
        return map[key]?.deref()
    }

    actual fun put(key: K, value: V) {
        map.put(key, WeakRef(value))
        registry.register(value, key, value)
    }

    actual fun toMap(): Map<K, V> {
        return map.mapValues { it.value.deref() }.filterNotNullValues()
    }
}
