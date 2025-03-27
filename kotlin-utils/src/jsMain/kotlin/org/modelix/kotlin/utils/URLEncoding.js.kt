package org.modelix.kotlin.utils

import js.uri.decodeURIComponent
import js.uri.encodeURIComponent

private val NULL_ENCODING = "%00"
private val SPECIAL_ENCODING = hashMapOf(
    '!' to "%21",
    '\'' to "%27",
    '(' to "%28",
    ')' to "%29",
    '~' to "%7E",
)

actual fun String?.urlEncode(): String {
    if (this == null) {
        return NULL_ENCODING
    }
    return encodeURIComponent(this).asSequence()
        .joinToString(separator = "") { SPECIAL_ENCODING[it] ?: it.toString() }
        .replace("%20", "+")
}

actual fun String.urlDecode(): String? {
    if (this == NULL_ENCODING) {
        return null
    }
    return decodeURIComponent(this.replace("+", " "))
}
