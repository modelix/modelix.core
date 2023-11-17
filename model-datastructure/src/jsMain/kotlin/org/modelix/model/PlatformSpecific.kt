package org.modelix.model

actual fun sleep(milliseconds: Long) {
    TODO("Not yet implemented")
}

actual fun bitCount(bits: Int): Int {
    var i = bits
    i -= (i ushr 1 and 0x55555555)
    i = (i and 0x33333333) + (i ushr 2 and 0x33333333)
    i = i + (i ushr 4) and 0x0f0f0f0f
    i += (i ushr 8)
    i += (i ushr 16)
    return i and 0x3f
}

actual fun <K, V> createLRUMap(size: Int): MutableMap<K, V> {
    return HashMap()
}

actual fun randomUUID(): String {
    return uuid.v4()
}
