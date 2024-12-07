package org.modelix.model.server.store

/**
 * A composed key used in the key-value stores to persist model data.
 * The key is composed of a [key] and an optional [repositoryId].
 * The [repositoryId] is set when an entry is scoped to the repository.
 *
 * This class is directly used with the Ignite cache (see [IgniteStoreClient]).
 * Therefore, the order of fields must be consistent with the configuration of `JdbcType` in ignite.xml.
 */
data class ObjectInRepository(private val repositoryId: String, val key: String) : Comparable<ObjectInRepository> {
    fun isGlobal() = repositoryId == ""
    fun getRepositoryId() = repositoryId.takeIf { it != "" }

    override fun compareTo(other: ObjectInRepository): Int {
        repositoryId.compareTo(other.repositoryId).let { if (it != 0) return it }
        return key.compareTo(other.key)
    }

    companion object {
        fun global(key: String) = ObjectInRepository("", key)
        fun create(repositoryId: String?, key: String) = ObjectInRepository(repositoryId ?: "", key)
    }
}
