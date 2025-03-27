package org.modelix.model

import kotlin.jvm.JvmStatic

class TreeId private constructor(val id: String) {
    override fun toString(): String = id

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other == null || this::class != other::class) return false

        other as TreeId

        return id == other.id
    }

    override fun hashCode(): Int {
        return id.hashCode()
    }

    companion object {
        val UUID_ID_PATTERN = Regex("""[0-9a-f]{8}-[0-9a-f]{4}-[0-5][0-9a-f]{3}-[089ab][0-9a-f]{3}-[0-9a-f]{12}""")

        @JvmStatic
        fun fromUUID(id: String): TreeId {
            require(id.matches(UUID_ID_PATTERN)) { "Invalid tree ID: $id" }
            return TreeId(id)
        }

        @JvmStatic
        fun fromLegacyId(id: String): TreeId = TreeId(id)

        @JvmStatic
        fun random(): TreeId {
            return fromUUID(randomUUID())
        }
    }
}
