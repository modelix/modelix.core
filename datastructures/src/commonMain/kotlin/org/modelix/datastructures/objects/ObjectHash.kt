package org.modelix.datastructures.objects

import kotlinx.serialization.Serializable
import org.kotlincrypto.hash.sha2.SHA256
import org.modelix.kotlin.utils.base64UrlEncoded
import kotlin.jvm.JvmInline

@JvmInline
@Serializable
value class ObjectHash(private val hash: String) : Comparable<ObjectHash> {
    init {
        require(isValidHashString(hash)) { "Not an object hash: $hash" }
    }

    override fun toString(): String {
        return hash
    }

    override fun compareTo(other: ObjectHash): Int {
        return hash.compareTo(other.hash)
    }

    companion object {
        val HASH_PATTERN = Regex("""[a-zA-Z0-9\-_]{5}\*[a-zA-Z0-9\-_]{38}""")

        fun isValidHashString(value: String?): Boolean {
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

        fun computeHash(serializedData: String): ObjectHash {
            return SHA256().digest(serializedData.encodeToByteArray()).base64UrlEncoded
                .let { it.substring(0, 5) + "*" + it.substring(5) }
                .let { ObjectHash(it) }
        }
    }
}
