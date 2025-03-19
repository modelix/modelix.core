package org.modelix.model.objects

import org.modelix.model.persistent.HashUtil
import kotlin.jvm.JvmInline

@JvmInline
value class ObjectHash(private val hash: String) {
    init {
        require(HashUtil.isSha256(hash)) { "Not an object hash: $hash" }
    }

    override fun toString(): String {
        return hash
    }

    companion object {
        fun computeHash(serializedData: String): ObjectHash {
            return ObjectHash(HashUtil.sha256(serializedData))
        }
    }
}
