package org.modelix.kotlin.utils

expect class WeakValueMap<K : Any, V : Any>() {
    fun get(key: K): V?
    fun put(key: K, value: V)
    fun toMap(): Map<K, V>
}

fun <K : Any, V : Any> WeakValueMap<K, V>.getOrPut(key: K, provider: () -> V): V {
    return get(key) ?: provider().also { put(key, it) }
}
