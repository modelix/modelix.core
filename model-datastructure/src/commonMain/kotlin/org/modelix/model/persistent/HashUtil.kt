package org.modelix.model.persistent

object HashUtil {
    val HASH_PATTERN = Regex("""[a-zA-Z0-9\-_]{5}\*[a-zA-Z0-9\-_]{38}""")

    fun sha256asByteArray(input: String): ByteArray = PlatformSpecificHashUtil.sha256asByteArray(input)

    fun sha256(input: String): String {
        val base64 = PlatformSpecificHashUtil.base64encode(sha256asByteArray(input))
        return base64.substring(0, 5) + "*" + base64.substring(5)
    }

    fun isSha256(value: String?): Boolean {
        // this implementation is equivalent to matching against HASH_PATTERN, but ~ 6 times faster
        if (value == null) return false
        if (value.length != 44) return false
        if (value[5] != '*') return false
        for (i in 0..4) {
            val c = value[i]
            if (c !in 'a'..'z' && c !in 'A'..'Z' && c !in '0'..'9' && c != '-' && c != '_') return false
        }
        for (i in 6..43) {
            val c = value[i]
            if (c !in 'a'..'z' && c !in 'A'..'Z' && c !in '0'..'9' && c != '-' && c != '_') return false
        }
        return true
    }

    fun extractSha256(input: String?): Iterable<String> {
        if (input == null) return emptyList()
        return HASH_PATTERN.findAll(input).map { it.groupValues.first() }.asIterable()
    }

    fun checkObjectHashes(entries: Map<String, String?>) {
        for (entry in entries) {
            checkObjectHash(entry.key, entry.value)
        }
    }

    fun checkObjectHash(providedHash: String, value: String?) {
        if (value == null) return
        if (!isSha256(providedHash)) return
        val computedHash = sha256(value)
        require(computedHash == providedHash) {
            val bytes = value.encodeToByteArray(throwOnInvalidSequence = true)
            "Provided hash $providedHash doesn't match the computed hash $computedHash for value: $value\n    Value as ByteArray$bytes"
        }
    }
}
