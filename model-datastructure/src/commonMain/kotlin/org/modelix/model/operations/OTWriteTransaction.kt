package org.modelix.model.operations

import org.modelix.model.ITransactionWrapper
import org.modelix.model.api.IBranch
import org.modelix.model.api.IChildLinkReference
import org.modelix.model.api.IConcept
import org.modelix.model.api.IConceptReference
import org.modelix.model.api.IIdGenerator
import org.modelix.model.api.INodeReference
import org.modelix.model.api.ITransaction
import org.modelix.model.api.ITree
import org.modelix.model.api.IWriteTransaction
import org.modelix.model.lazy.DuplicateNodeId
import org.modelix.model.unwrapAll

class OTWriteTransaction(
    private val transaction: IWriteTransaction,
    private val otBranch: OTBranch,
    private var idGenerator: IIdGenerator,
) : IWriteTransaction, ITransactionWrapper {
    private val logger = mu.KotlinLogging.logger {}
    override fun unwrap(): ITransaction = transaction

    fun apply(op: IOperation) {
        logger.trace { op.toString() }
        val appliedOp = op.apply(transaction)
        otBranch.operationApplied(appliedOp)
    }

    override fun moveChild(newParentId: Long, newRole: String?, newIndex_: Int, childId: Long) {
        val newIndex = if (newIndex_ != -1) newIndex_ else getChildren(newParentId, newRole).count()

        val newPosition = PositionInRole(newParentId, IChildLinkReference.fromLegacyApi(newRole), newIndex)
        val currentRole = RoleInNode(transaction.getParent(childId), IChildLinkReference.fromLegacyApi(transaction.getRole(childId)))
        val currentIndex = transaction.getChildren(currentRole.nodeId, currentRole.role.stringForLegacyApi()).indexOf(childId)
        val currentPosition = PositionInRole(currentRole, currentIndex)
        if (currentPosition == newPosition) return

        apply(MoveNodeOp(childId, newPosition))
    }

    override fun setProperty(nodeId: Long, role: String, value: String?) {
        apply(SetPropertyOp(nodeId, role, value))
    }

    override fun setReferenceTarget(sourceId: Long, role: String, target: INodeReference?) {
        apply(SetReferenceOp(sourceId, role, target))
    }

    override fun addNewChild(parentId: Long, role: String?, index: Int, childId: Long, concept: IConceptReference?) {
        var index_ = index
        if (index_ == -1) {
            index_ = getChildren(parentId, role).count()
        }
        apply(AddNewChildOp(PositionInRole(parentId, IChildLinkReference.fromLegacyApi(role), index_), childId, concept))
    }

    override fun addNewChildren(
        parentId: Long,
        role: String?,
        index: Int,
        childIds: LongArray,
        concepts: Array<IConceptReference?>,
    ) {
        if (childIds.isEmpty()) return
        val index = if (index != -1) index else getChildren(parentId, role).count()
        apply(AddNewChildrenOp(PositionInRole(parentId, IChildLinkReference.fromLegacyApi(role), index), childIds, concepts))
    }

    override fun addNewChild(parentId: Long, role: String?, index: Int, childId: Long, concept: IConcept?) {
        return addNewChild(parentId, role, index, childId, concept?.getReference())
    }

    override fun deleteNode(nodeId: Long) {
        getAllChildren(nodeId).forEach { deleteNode(it) }
        apply(DeleteNodeOp(nodeId))
    }

    override fun addNewChild(parentId: Long, role: String?, index: Int, concept: IConcept?): Long {
        return addNewChild(parentId, role, index, concept?.getReference())
    }

    override fun addNewChild(parentId: Long, role: String?, index: Int, concept: IConceptReference?): Long {
        return try {
            val childId = idGenerator.generate()
            addNewChild(parentId, role, index, childId, concept)
            childId
        } catch (dni: DuplicateNodeId) {
            addNewChild(parentId, role, index, concept)
        }
    }

    override fun addNewChildren(
        parentId: Long,
        role: String?,
        index: Int,
        concepts: Array<IConceptReference?>,
    ): LongArray {
        return try {
            val childIds = concepts.map { idGenerator.generate() }.toLongArray()
            addNewChildren(parentId, role, index, childIds, concepts)
            childIds
        } catch (dni: DuplicateNodeId) {
            addNewChildren(parentId, role, index, concepts)
        }
    }

    override fun setConcept(nodeId: Long, concept: IConceptReference?) {
        apply(SetConceptOp(nodeId, concept))
    }

    override fun containsNode(nodeId: Long): Boolean {
        return transaction.containsNode(nodeId)
    }

    override fun getAllChildren(parentId: Long): Iterable<Long> {
        return transaction.getAllChildren(parentId)
    }

    override val branch: IBranch
        get() = otBranch

    override fun getChildren(parentId: Long, role: String?): Iterable<Long> {
        return transaction.getChildren(parentId, role)
    }

    override fun getConcept(nodeId: Long): IConcept? {
        return transaction.getConcept(nodeId)
    }

    override fun getConceptReference(nodeId: Long): IConceptReference? {
        return transaction.getConceptReference(nodeId)
    }

    override fun getParent(nodeId: Long): Long {
        return transaction.getParent(nodeId)
    }

    override fun getProperty(nodeId: Long, role: String): String? {
        return transaction.getProperty(nodeId, role)
    }

    override fun getReferenceTarget(sourceId: Long, role: String): INodeReference? {
        return transaction.getReferenceTarget(sourceId, role)
    }

    override fun getRole(nodeId: Long): String? {
        return transaction.getRole(nodeId)
    }

    override fun getReferenceRoles(sourceId: Long): Iterable<String> {
        return transaction.getReferenceRoles(sourceId)
    }

    override fun getPropertyRoles(sourceId: Long): Iterable<String> {
        return transaction.getPropertyRoles(sourceId)
    }

    override var tree: ITree
        get() = transaction.tree
        set(tree) {
            throw UnsupportedOperationException()
        }

    override fun getUserObject(key: Any): Any? {
        return transaction.getUserObject(key)
    }

    override fun putUserObject(key: Any, value: Any?) {
        transaction.putUserObject(key, value)
    }
}

fun IWriteTransaction.applyOperation(op: IOperation) {
    this.unwrapAll().filterIsInstance<OTWriteTransaction>().first().apply(op)
}
