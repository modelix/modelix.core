package org.modelix.kotlin.utils

import java.net.URLDecoder
import java.net.URLEncoder
import java.nio.charset.StandardCharsets

actual fun String?.urlEncode(): String {
    return if (this == null) {
        "%00"
    } else {
        URLEncoder.encode(this, StandardCharsets.UTF_8)
    }
}

actual fun String.urlDecode(): String? {
    return if ("%00" == this) {
        null
    } else {
        URLDecoder.decode(this, StandardCharsets.UTF_8)
    }
}
