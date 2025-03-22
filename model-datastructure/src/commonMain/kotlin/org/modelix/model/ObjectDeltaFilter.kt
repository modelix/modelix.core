package org.modelix.model

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

@Serializable
data class ObjectDeltaFilter(
    /**
     * Hashes of version objects that are already available and should be excluded.
     * The traversal stops at these hashes and all transitively referenced objects are also excluded.
     */
    val knownVersions: Set<String> = emptySet(),

    /**
     * Objects required to access the operations of a version. They are usually only required for merging versions.
     * If a client doesn't do local merges, but let the server do the merge, the operations are never accessed and can
     * be excluded.
     */
    val includeOperations: Boolean = true,

    /**
     * If false, then the requested version is the only included version object.
     */
    val includeHistory: Boolean = true,

    /**
     * If false, then only the version objects are included, but not their tree data.
     */
    val includeTrees: Boolean = true,
) {
    constructor(baseVersion: String?) : this(knownVersions = setOfNotNull(baseVersion))

    fun toJson() = Json.encodeToString(this)

    companion object {
        fun fromJson(serializedJson: String): ObjectDeltaFilter = Json.decodeFromString(serializedJson)
    }
}
