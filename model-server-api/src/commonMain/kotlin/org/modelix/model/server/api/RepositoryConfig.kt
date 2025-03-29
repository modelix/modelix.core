package org.modelix.model.server.api

import kotlinx.serialization.Serializable

@Serializable
data class RepositoryConfig(
    val legacyNameBasedRoles: Boolean = false,

    /**
     * Earlier versions of Modelix stored all objects into a single global object store.
     * Newer versions maintain a separate store for each repository, which has security advantages and also simplifies
     * the garbage collection after deleting a repository, but since the repository
     */
    val legacyGlobalStorage: Boolean = false,

    /**
     * Earlier versions of Modelix used 64-bit integers, but new repositories should use strings.
     */
    val nodeIdType: NodeIdType = NodeIdType.STRING,

    /**
     * For efficient versioning and replication the model data has to be stored in a tree shaped data structure.
     * Different data structures have different properties and trade-offs. That's why a different type of tree data
     * structure can be chosen for the model.
     */
    val primaryTreeType: TreeType = TreeType.PATRICIA_TRIE,

    /**
     * Is assigned when the repository is created and cannot be changed later.
     * A branch or fork of the repository will use the same model ID.
     * It's usually a UUID and used when generating ID for new nodes inside this repository that look like this:
     *
     *   `modelix:7a77e986-0f4b-4b2e-8eb0-71057c6cd46f/4c3000003eb`
     *
     */
    val modelId: String,

    /**
     * Is assigned when the repository is created and cannot be changed later.
     * A fork will have a different [repositoryId], but the same [modelId].
     * The [repositoryId] can be used instead of the [repositoryName] in URLs to prevent them from breaking
     * after a rename.
     */
    val repositoryId: String,

    /**
     * Some human-readable identifier. Can be changed later.
     */
    val repositoryName: String,

    /**
     * Names under which the repository can also be found. Usually used to store the previous name after renaming.
     */
    val alternativeNames: Set<String> = emptySet(),
) {

    fun rename(newName: String): RepositoryConfig = copy(
        repositoryName = newName,
        alternativeNames = alternativeNames + repositoryName,
    )

    /**
     * All strings that can be used to reference this repository in a URL.
     */
    fun getAliases(): Set<String> = setOf(repositoryId) + repositoryName + alternativeNames

    /**
     * There are the following properties of a tree that are relevant for our use case.
     *
     * # Balancing
     * An ideal tree has a low maximum **height** and a small **node size**.
     * The height determines how many traversals (requests) are necessary to access an entry.
     * The node size determines how much data is duplicated by a new version.
     * If you try to optimize both, larger **rebalancings** are necessary after a changes which increases the delta
     * between two versions and reduces it's efficiency.
     *
     * # Predictable Shape
     * Models are stored as hash trees which has the advantage that the content of two (sub-)trees can be compared by
     * just comparing the hash. Computing a delta between two versions is very efficient and can even be done without
     * downloading the whole model.
     * This efficient diffing is only possible if two trees containing the same data have exactly the same shape,
     * independent of the order in which the data was inserted into the tree.
     *
     * Some data structures that are efficient for traditional databases are not suitable for our approach. For example,
     * _B-Trees_ optimize for a low tree height and small rebalancing operations, but if inserting an entry triggers a
     * rebalancing, removing the entry directly after will not cause the tree to rebalance back into its previous shape.
     * The two trees contain the same data, but cannot be compared efficiently.
     *
     */
    enum class TreeType {
        /**
         * Early versions of modelix used a hash array mapped trie (HAMT) with 64-bit integers and a 64-bit long hashes,
         * which removes the need to handle hash collisions (they don't exist).
         * The current implementation also supports other key types and handles hash collisions.
         *
         * A HAMT has good balancing properties while it's shape still doesn't depend on the insertion order.
         * Entries are always inserted into the same path of the tree.
         * The number of child nodes is limited to 32 (each level uses 5 bits of the hash).
         * In a tree without hash collisions this limits the height to 13 (64 / 5).
         * Nodes with a single child are merged to further reduce the tree height.
         *
         * If sequential values for the keys are used, the entries will end up in the same subtree,
         * which increases the average number of children per node and reduces the tree height.
         */
        HASH_ARRAY_MAPPED_TRIE,

        /**
         * A trie is a tree where keys with the same prefix are stored in the same subtree.
         *
         * While the HASH_ARRAY_MAPPED_TRIE when used with the key itself as the hash has the desirable property of
         * putting similar values into the same subtree, this isn't the case anymore when strings are used for the key.
         * [Any.hashCode] is usually implemented in a way that it tries to avoid collisions. A small difference in the
         * value causes a big change in the hashCode, giving it similar properties as a random value.
         * Random keys reduce the average size of a node, but increase the height.
         * For a general purpose in-memory persistent data structure this is fine
         * (the lower data duplication is more relevant than all the other properties).
         *
         * A general trie uses the key itself to derive a common prefix for the subtrees.
         * A patricia trie just optimizes the tree height by merging nodes with single children.
         * The [HASH_ARRAY_MAPPED_TRIE] with 64-bit integer values effectively makes it a patricia trie and this
         * implementation just has similar properties when strings are used for the keys.
         *
         * Using strings instead of integers for the IDs is useful in case the model is imported from a different data
         * source. Then the original IDs can be used and the efficient diffing even works between a model stored in
         * Modelix and the original data source, which is relevant during a re-import.
         */
        PATRICIA_TRIE,
    }

    enum class NodeIdType {
        INT64, STRING,
    }
}
