package org.modelix.model.persistent

fun nullAsEmptyString(str: String?): String {
    if (str == null) return ""
    if (str.isEmpty()) throw RuntimeException("Empty string not allowed")
    return str
}

fun emptyStringAsNull(str: String): String? {
    return if (str.isEmpty()) null else str
}

expect object SerializationUtil {
    fun escape(value: String?): String
    fun unescape(value: String?): String?
    fun longToHex(value: Long): String
    fun longFromHex(hex: String): Long
    fun intToHex(value: Int): String
    fun intFromHex(hex: String): Int
}
