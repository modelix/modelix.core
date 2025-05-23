package org.modelix.mps.multiplatform.model

/**
 * Restores the string back from escaped version.
 *
 * @throws IllegalArgumentException on invalid escape sequences
 */
fun unescapeRefChars(text: String?): String? {
    if (text == null || text.indexOf('%') < 0) {
        return text
    }
    val sb = StringBuilder()
    val len = text.length
    var i = 0
    while (i < len) {
        var c = text.get(i)
        if (c == '%') {
            require(i + 2 < len) { "incomplete escape sequence: `" + text.substring(i) + "'" }
            val hi: Int = decode(text[++i])
            val lo: Int = decode(text[++i])
            require(!(hi == -1 || lo == -1)) { "invalid escape sequence: `" + text.substring(i - 2) + "'" }
            c = (((hi and 0xf) shl 4) or (lo and 0xf)).toChar()
        }
        sb.append(c)
        i++
    }
    return sb.toString()
}

private fun decode(c: Char): Int {
    if ((c >= '0') && (c <= '9')) return c.code - '0'.code
    if ((c >= 'a') && (c <= 'f')) return c.code - 'a'.code + 10
    if ((c >= 'A') && (c <= 'F')) return c.code - 'A'.code + 10
    return -1
}

/**
 * Escapes all characters which can be used as separators in all kinds of MPS references (like node/model/module/etc).
 */
fun escapeRefChars(text: String?): String? {
    if (text == null || text.isEmpty()) {
        return text
    }
    val sb = StringBuilder()
    val len = text.length
    for (i in 0 until len) {
        val c = text.get(i)
        when (c) {
            '%', '(', ')', '/' -> {
                sb.append('%')
                sb.append(HEX_DIGITS[(c.code shr 4) and 0x0f])
                sb.append(HEX_DIGITS[(c).code and 0x0f])
            }

            else -> sb.append(c)
        }
    }
    return sb.toString()
}

val HEX_DIGITS = charArrayOf('0', '1', '2', '3', '4', '5', '6', '7', '8', '9', 'A', 'B', 'C', 'D', 'E', 'F')
