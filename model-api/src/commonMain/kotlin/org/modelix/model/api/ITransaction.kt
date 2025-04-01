package org.modelix.model.api

/**
 * Provides transactional access to nodes in the tree of this transaction.
 */
interface ITransaction {
    val branch: IBranch
    val tree: ITree

    /**
     * Checks if the given node is contained in the tree of this transaction.
     *
     * @param nodeId id of the desired node
     * @return true, if the node is contained in the tree, or false, otherwise
     */
    fun containsNode(nodeId: Long): Boolean

    /**
     * Returns the concept of the given node in the tree of this transaction.
     *
     * @param nodeId id of the desired node
     * @return concept of the node or null, if the concept could not be found
     */
    fun getConcept(nodeId: Long): IConcept?

    /**
     * Returns a reference to the concept of the given node in the tree of this transaction.
     *
     * @param nodeId id of the desired node
     * @return reference to the concept of the node or null, if the concept could not be found
     */
    fun getConceptReference(nodeId: Long): IConceptReference?

    /**
     * Returns the id of the parent node of the given node in the tree of this transaction.
     *
     * @param nodeId id of the desired node
     * @return node id of the parent node
     */
    fun getParent(nodeId: Long): Long

    /**
     * Returns the role of the given node within its parent node in the tree of this transaction.
     *
     * @param nodeId id of the desired node
     * @return name of the role
     */
    fun getRole(nodeId: Long): String?

    /**
     * Returns the property value of the given property role for the given node in the tree of this transaction.
     *
     * @param nodeId id of the desired node
     * @param role name of the property role
     * @return value of the property for the given node
     */
    fun getProperty(nodeId: Long, role: String): String?

    /**
     * Returns the target of the given reference role for the given node in the tree of this transaction.
     *
     * @param sourceId id of the source node
     * @param role name of the reference role
     * @return node reference to the target
     */
    fun getReferenceTarget(sourceId: Long, role: String): INodeReference?

    /**
     * Returns the children of the child link for the given node in the tree of this transaction
     *
     * @param parentId id of the desired node
     * @param role name of the child link
     * @return iterable over the child ids
     */
    fun getChildren(parentId: Long, role: String?): Iterable<Long>

    /**
     * Returns all children of the given node in the tree of this transaction.
     *
     * @param parentId id of the desired node
     * @return iterable over the child ids
     */
    fun getAllChildren(parentId: Long): Iterable<Long>

    /**
     * Returns all reference roles for the given node in the tree of this transaction.
     *
     * @param sourceId id of the desired node
     * @return iterable over the reference role names
     */
    fun getReferenceRoles(sourceId: Long): Iterable<String>

    /**
     * Returns all property roles for the given node in the tree of this transaction.
     *
     * @param sourceId id of the desired node
     * @return iterable over the property role names
     */
    fun getPropertyRoles(sourceId: Long): Iterable<String>

    /**
     * Returns the user object with the given key.
     *
     * @param key of the desired user object
     * @return user object or null, if the key does not exist or the user object is null
     */
    fun getUserObject(key: Any): Any?

    /**
     * Stores a user object by using the specified key.
     *
     * @param key key to be used
     * @param value the user object to be stored
     */
    fun putUserObject(key: Any, value: Any?)
}
