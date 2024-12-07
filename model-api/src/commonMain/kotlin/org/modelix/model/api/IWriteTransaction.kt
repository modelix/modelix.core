package org.modelix.model.api

/**
 * Provides transactional read and write access to nodes.
 */
interface IWriteTransaction : ITransaction {
    override var tree: ITree

    /**
     * Sets the value of the given node for the given property role to the specified value.
     *
     * @param nodeId id of the desired node
     * @param role property role, for which the value should be set
     * @param value the new property value
     */
    fun setProperty(nodeId: Long, role: String, value: String?)

    /**
     * Sets the reference target of the given node for the given reference role to the specified target.
     *
     * @param sourceId id of the source node
     * @param role reference role, for which the target should be set
     * @param target the new reference target
     */
    fun setReferenceTarget(sourceId: Long, role: String, target: INodeReference?)

    /**
     * Moves a node within the tree of this transaction.
     *
     * @param newParentId id of the new parent node
     * @param newRole new role within the parent node
     * @param newIndex index within the role
     * @param childId id of the node to be moved
     *
     * @throws RuntimeException when trying to move the root node
     *                          or if the node specified by newParentID is a descendant of the node specified by childId
     */
    fun moveChild(newParentId: Long, newRole: String?, newIndex: Int, childId: Long)

    /**
     * Creates and adds a new child of the given concept to the tree of this transaction.
     *
     * @param parentId id of the parent node
     * @param role child link role within the parent node
     * @param index index within the child link role
     * @param concept concept of the new node
     *
     * @return id of the newly created node
     */
    fun addNewChild(parentId: Long, role: String?, index: Int, concept: IConcept?): Long

    /**
     * Creates and adds a new child of the given concept to the tree of this transaction.
     *
     * @param parentId id of the parent node
     * @param role child link role within the parent node
     * @param index index within the child link role
     * @param concept concept reference to the concept of the new node
     *
     * @return id of the newly created node
     */
    fun addNewChild(parentId: Long, role: String?, index: Int, concept: IConceptReference?): Long

    /**
     * Creates and adds a new child of the given concept to the tree of this transaction.
     *
     * @param parentId id of the parent node
     * @param role child link role within the parent node
     * @param index index within the child link role
     * @param childId the id to be used for creation
     * @param concept the concept of the new node
     *
     * @throws [RuntimeException] if the childId already exists
     */
    fun addNewChild(parentId: Long, role: String?, index: Int, childId: Long, concept: IConcept?)

    /**
     * Creates and adds a new child of the given concept to the tree of this transaction.
     *
     * @param parentId id of the parent node
     * @param role child link role within the parent node
     * @param index index within the child link role
     * @param childId the id to be used for creation
     * @param concept concept reference to the concept of the new node
     *
     * @throws [RuntimeException] if the childId already exists
     */
    fun addNewChild(parentId: Long, role: String?, index: Int, childId: Long, concept: IConceptReference?)

    fun addNewChildren(parentId: Long, role: String?, index: Int, concepts: Array<IConceptReference?>): LongArray
    fun addNewChildren(parentId: Long, role: String?, index: Int, childIds: LongArray, concepts: Array<IConceptReference?>)

    /**
     * Sets the concept of the node identified by the given id.
     *
     * @param nodeId id of the node
     * @param concept the new concept, or null to remove the concept
     */
    fun setConcept(nodeId: Long, concept: IConceptReference?)

    /**
     * Deletes the given node.
     *
     * @param nodeId id of the node to be deleted
     */
    fun deleteNode(nodeId: Long)
}
