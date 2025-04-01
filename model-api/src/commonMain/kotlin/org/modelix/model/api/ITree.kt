package org.modelix.model.api

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.asFlow
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flatMapConcat
import kotlinx.coroutines.flow.flattenConcat
import kotlinx.coroutines.flow.flowOf
import org.modelix.model.api.async.IAsyncMutableTree

/**
 * Consists of [INode]s.
 */
interface ITree {
    fun asObject(): Any

    fun asAsyncTree(): IAsyncMutableTree

    /**
     * Checks whether this tree uses uids or names for roles.
     *
     * @return true, if the tree uses uids for roles, or
     *         false, if the tree uses names for roles
     */
    fun usesRoleIds(): Boolean

    /**
     * Returns the id of this tree.
     *
     * @return id of the tree
     */
    fun getId(): String

    /**
     * Propagates changes to the given visitor.
     *
     * @param oldVersion the old state of the tree
     * @param visitor the given visitor
     */
    fun visitChanges(oldVersion: ITree, visitor: ITreeChangeVisitor)

    /**
     * Checks if the given node is contained in this tree.
     *
     * @param nodeId id of the desired node
     * @return true, if the node is contained in this tree, or false, otherwise
     */
    fun containsNode(nodeId: Long): Boolean

    /**
     * Returns the property value of the given property role for the given node in this tree.
     *
     * @param nodeId id of the desired node
     * @param role name or id of the property role
     * @return value of the property for the given node
     */
    fun getProperty(nodeId: Long, role: String): String?

    /**
     * Returns the children of the child link for the given node in this tree.
     *
     * @param parentId id of the desired node
     * @param role name or id of the child link
     * @return iterable over the child ids
     */
    fun getChildren(parentId: Long, role: String?): Iterable<Long>

    /**
     * Returns the concept of the given node in this tree.
     *
     * @param nodeId id of the desired node
     * @return concept of the node or null, if the concept could not be found
     */
    fun getConcept(nodeId: Long): IConcept?

    /**
     * Returns a reference to the concept of the given node in this tree.
     *
     * @param nodeId id of the desired node
     * @return reference to the concept of the node or null, if the concept could not be found
     */
    fun getConceptReference(nodeId: Long): IConceptReference?

    /**
     * Returns the id of the parent node of the given node in this.
     *
     * @param nodeId id of the desired node
     * @return node id of the parent node
     */
    fun getParent(nodeId: Long): Long

    /**
     * Returns the role of the given node within its parent node in this tree.
     *
     * @param nodeId id of the desired node
     * @return name of the role
     */
    fun getRole(nodeId: Long): String?

    /**
     * Sets the value of the given node for the given property role to the specified value.
     *
     * @param nodeId id of the desired node
     * @param role property role, for which the value should be set
     * @param value the new property value
     *
     * @return tree with changes
     */
    fun setProperty(nodeId: Long, role: String, value: String?): ITree

    /**
     * Returns the target of the given reference role for the given node in this tree.
     *
     * @param sourceId id of the source node
     * @param role name or id of the reference role
     * @return node reference to the target
     */
    fun getReferenceTarget(sourceId: Long, role: String): INodeReference?

    /**
     * Sets the reference target of the given node for the given reference role to the specified target.
     *
     * @param sourceId id of the source node
     * @param role reference role, for which the target should be set
     * @param target the new reference target
     *
     * @return tree with changes
     */
    fun setReferenceTarget(sourceId: Long, role: String, target: INodeReference?): ITree

    fun setReferenceTarget(sourceId: Long, role: String, targetId: Long): ITree =
        setReferenceTarget(sourceId, role, LocalPNodeReference(targetId))

    /**
     * Returns all reference roles for the given node in this tree.
     *
     * @param sourceId id of the desired node
     * @return iterable over the reference role names or ids
     */
    fun getReferenceRoles(sourceId: Long): Iterable<String>

    /**
     * Returns all property roles for the given node in this tree.
     *
     * @param sourceId id of the desired node
     * @return iterable over the property role names or ids
     */
    fun getPropertyRoles(sourceId: Long): Iterable<String>

    /**
     * Returns all child link roles for the given node in this tree.
     *
     * @param sourceId id of the desired node
     * @return iterable over the child link names or ids
     */
    fun getChildRoles(sourceId: Long): Iterable<String?>

    /**
     * Returns all children of the given node in this tree.
     *
     * @param parentId id of the desired node
     * @return iterable over the child ids
     */
    fun getAllChildren(parentId: Long): Iterable<Long>

    /**
     * Moves a node within this tree.
     *
     * @param newParentId id of the new parent node
     * @param newRole new role within the parent node
     * @param newIndex index within the role
     * @param childId id of the node to be moved
     *
     * @throws RuntimeException when trying to move the root node
     *                          or if the node specified by newParentID is a descendant of the node specified by childId
     */
    fun moveChild(newParentId: Long, newRole: String?, newIndex: Int, childId: Long): ITree

    /**
     * Creates and adds a new child of the given concept to this tree.
     *
     * @param parentId id of the parent node
     * @param role child link role within the parent node
     * @param index index within the child link role
     * @param childId the id to be used for creation
     * @param concept the concept of the new node
     * @return tree with changes
     *
     * @throws [RuntimeException] if the childId already exists
     */
    fun addNewChild(parentId: Long, role: String?, index: Int, childId: Long, concept: IConcept?): ITree

    /**
     * Creates and adds a new child of the given concept to this tree.
     *
     * @param parentId id of the parent node
     * @param role child link role within the parent node
     * @param index index within the child link role
     * @param concept concept reference to the concept of the new node
     * @return tree with changes
     */
    fun addNewChild(parentId: Long, role: String?, index: Int, childId: Long, concept: IConceptReference?): ITree

    /**
     * Creates and adds multiple new children of the given concepts to this tree.
     *
     * @param parentId id of the parent node
     * @param role child link role within the parent node
     * @param index index within the child link role
     * @param newIds the ids to be used for creation
     * @param concepts the concepts of the new nodes
     * @return tree with changes
     *
     * @throws [RuntimeException] if the childId already exists
     */
    fun addNewChildren(parentId: Long, role: String?, index: Int, newIds: LongArray, concepts: Array<IConcept?>): ITree

    /**
     * Creates and adds multiple new children of the given concepts to this tree.
     *
     * @param parentId id of the parent node
     * @param role child link role within the parent node
     * @param index index within the child link role
     * @param newIds the ids to be used for creation
     * @param concepts concept references to the concepts of the new nodes
     * @return tree with changes
     *
     * @throws [RuntimeException] if the childId already exists
     */
    fun addNewChildren(parentId: Long, role: String?, index: Int, newIds: LongArray, concepts: Array<IConceptReference?>): ITree

    /**
     * Deletes the given node.
     *
     * @param nodeId id of the node to be deleted
     */
    fun deleteNode(nodeId: Long): ITree

    /**
     * Deletes the given nodes.
     *
     * @param nodeIds array of node ids to be deleted
     */
    fun deleteNodes(nodeIds: LongArray): ITree

    /**
     * @param nodeId id of the node
     * @param concept the concept ref of the new concept for the node, or null to remove the concept
     */
    fun setConcept(nodeId: Long, concept: IConceptReference?): ITree

    fun getAllChildrenAsFlow(parentId: Long): Flow<Long> = getAllChildren(parentId).asFlow()
    fun getDescendantsAsFlow(nodeId: Long, includeSelf: Boolean = false): Flow<Long> {
        return if (includeSelf) {
            flowOf(flowOf(nodeId), getDescendantsAsFlow(nodeId, false)).flattenConcat()
        } else {
            getAllChildrenAsFlow(nodeId).flatMapConcat { getDescendantsAsFlow(it, true) }
        }
    }
    fun getAllReferenceTargetsAsFlow(nodeId: Long): Flow<Pair<String, INodeReference>> = getReferenceRoles(nodeId).map { it to getReferenceTarget(nodeId, it) }.filterSecondNotNull().asFlow()
    fun getChildrenAsFlow(parentId: Long, role: String?): Flow<Long> = getChildren(parentId, role).asFlow()
    fun getReferenceTargetAsFlow(nodeId: Long, role: String): Flow<INodeReference> = flowOf(getReferenceTarget(nodeId, role)).filterNotNull()
    fun getParentAsFlow(nodeId: Long): Flow<Long> = flowOf(getParent(nodeId)).filter { it != 0L }
    fun getPropertyValueAsFlow(nodeId: Long, role: String): Flow<String?> = flowOf(getProperty(nodeId, role))

    companion object {
        const val ROOT_ID = 1L
        const val DETACHED_NODES_ROLE = "detached"
        val DETACHED_NODES_LINK = IChildLinkReference.fromIdAndName(DETACHED_NODES_ROLE, DETACHED_NODES_ROLE)
    }
}
