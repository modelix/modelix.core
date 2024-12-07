package org.modelix.model.persistent

import java.net.URLDecoder
import java.net.URLEncoder
import java.nio.charset.StandardCharsets

actual object SerializationUtil {
    actual fun escape(value: String?): String {
        return if (value == null) {
            "%00"
        } else {
            URLEncoder.encode(value, StandardCharsets.UTF_8)
        }
    }

    actual fun unescape(value: String?): String? {
        if (value == null) {
            return null
        }
        return if ("%00" == value) {
            null
        } else {
            URLDecoder.decode(value, StandardCharsets.UTF_8)
        }
    }

    actual fun longToHex(value: Long): String {
        return java.lang.Long.toHexString(value)
    }

    actual fun longFromHex(hex: String): Long {
        return java.lang.Long.parseUnsignedLong(hex, 16)
    }

    actual fun intToHex(value: Int): String {
        return Integer.toHexString(value)
    }

    actual fun intFromHex(hex: String): Int {
        return Integer.parseUnsignedInt(hex, 16)
    }
}
