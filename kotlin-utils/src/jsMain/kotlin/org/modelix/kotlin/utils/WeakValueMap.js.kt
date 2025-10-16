package org.modelix.kotlin.utils

import js.memory.FinalizationRegistry
import js.memory.WeakRef

actual class WeakValueMap<K : Any, V : Any> {
    private val map = LinkedHashMap<K, WeakRef<V>>()

    /**
     * Quote from the documentation at
     * https://developer.mozilla.org/en-US/docs/Web/JavaScript/Reference/Global_Objects/FinalizationRegistry#callbacks_never_called_synchronously
     * > No matter how much pressure you put on the garbage collector, the cleanup callback will never be called
     * > synchronously. The object may be reclaimed synchronously, but the callback will always be called sometime after
     * > the current job finishes.
     *
     * For the following sequence
     *
     * 1. Object is reclaimed
     * 2. getOrPut is called on this map
     * 3. Callback of FinalizationRegistry is called
     *
     * the map will already contain a new value for the key that the FinalizationRegistry tries to clean up.
     * In that case calling `map.remove(it)` would be wrong. We have to check is the entry changed in the meantime.
     */
    private val registry = FinalizationRegistry<K> {
        if (map[it]?.deref() == null) {
            map.remove(it)
        }
    }

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
