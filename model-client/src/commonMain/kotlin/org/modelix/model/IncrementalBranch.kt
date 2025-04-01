package org.modelix.model

import org.modelix.incremental.DependencyTracking
import org.modelix.incremental.IStateVariableGroup
import org.modelix.incremental.IStateVariableReference
import org.modelix.model.api.IBranch
import org.modelix.model.api.IBranchListener
import org.modelix.model.api.IBranchWrapper
import org.modelix.model.api.IConcept
import org.modelix.model.api.IConceptReference
import org.modelix.model.api.INodeReference
import org.modelix.model.api.IReadTransaction
import org.modelix.model.api.ITransaction
import org.modelix.model.api.ITree
import org.modelix.model.api.ITreeChangeVisitor
import org.modelix.model.api.ITreeChangeVisitorEx
import org.modelix.model.api.IWriteTransaction
import org.modelix.model.api.async.IAsyncMutableTree
import org.modelix.model.api.runSynchronized

class IncrementalBranch(val branch: IBranch) : IBranch, IBranchWrapper {

    companion object {
        private val LOG = mu.KotlinLogging.logger {}
    }

    init {
        branch.addListener(object : IBranchListener {
            override fun treeChanged(oldTree: ITree?, newTree: ITree) {
                // The IncrementalBranch is used in the UI, but there might be changes done directly to the non-wrapped
                // branch (e.g., if it's coming from a ReplicatedModel) which we can only detect with this listener.
                // TODO If the changes are done through the IncrementalBranch then there are duplicate events,
                //      which is a performance issue (iterating the changes here is unnecessary).
                createEventsForTreeChange(oldTree, newTree)
            }
        })
    }

    private fun createEventsForTreeChange(oldTree: ITree?, newTree: ITree) {
        if (oldTree == null) return

        newTree.visitChanges(
            oldTree,
            object : ITreeChangeVisitorEx {
                override fun containmentChanged(nodeId: Long) {
                    modified(ContainmentDependency(this@IncrementalBranch, nodeId))
                }

                override fun conceptChanged(nodeId: Long) {
                    modified(ConceptDependency(this@IncrementalBranch, nodeId))
                }

                override fun childrenChanged(nodeId: Long, role: String?) {
                    modified(ChildrenDependency(this@IncrementalBranch, nodeId, role))
                }

                override fun referenceChanged(nodeId: Long, role: String) {
                    modified(ReferenceDependency(this@IncrementalBranch, nodeId, role))
                }

                override fun propertyChanged(nodeId: Long, role: String) {
                    modified(PropertyDependency(this@IncrementalBranch, nodeId, role))
                }

                override fun nodeAdded(nodeId: Long) {
                    modified(UnclassifiedNodeDependency(this@IncrementalBranch, nodeId))
                }

                override fun nodeRemoved(nodeId: Long) {
                    modified(UnclassifiedNodeDependency(this@IncrementalBranch, nodeId))
                }
            },
        )
    }

    override fun unwrapBranch(): IBranch {
        return branch
    }

    fun accessed(dep: IStateVariableReference<*>) {
        DependencyTracking.accessed(dep)
    }

    fun modified(dep: IStateVariableReference<*>) {
        DependencyTracking.modified(dep)
    }

    override fun getId(): String = branch.getId()

    override fun runReadT(f: (IReadTransaction) -> Unit) {
        computeReadT(f)
    }

    override fun <T> computeReadT(computable: (IReadTransaction) -> T): T {
        return branch.computeReadT { computable(IncrementalReadTransaction(it)) }
    }

    override fun runWriteT(f: (IWriteTransaction) -> Unit) {
        computeWriteT(f)
    }

    override fun <T> computeWriteT(computable: (IWriteTransaction) -> T): T {
        return branch.computeWriteT {
            computable(IncrementalWriteTransaction(it))
        }
    }

    override fun runRead(runnable: () -> Unit) {
        return computeRead(runnable)
    }

    override fun <T> computeRead(computable: () -> T): T {
        return computeReadT { computable() }
    }

    override fun runWrite(runnable: () -> Unit) {
        computeWrite(runnable)
    }

    override fun <T> computeWrite(computable: () -> T): T {
        return computeWriteT { computable() }
    }

    override fun canRead(): Boolean {
        return branch.canRead()
    }

    override fun canWrite(): Boolean {
        return branch.canWrite()
    }

    override val transaction: ITransaction get() {
        return when (val t = branch.transaction) {
            is IWriteTransaction -> IncrementalWriteTransaction(t)
            is IReadTransaction -> IncrementalReadTransaction(t)
            else -> throw RuntimeException("Unknown transaction type: $t")
        }
    }
    override val readTransaction: IReadTransaction
        get() = IncrementalReadTransaction(branch.readTransaction)
    override val writeTransaction: IWriteTransaction
        get() = IncrementalWriteTransaction(branch.writeTransaction)

    private val branchListeners = LinkedHashSet<IBranchListener>()
    private val forwardingBranchListener: IBranchListener = object : IBranchListener {
        override fun treeChanged(oldTree: ITree?, newTree: ITree) {
            branchListeners.forEach {
                try {
                    it.treeChanged(oldTree?.wrap(), newTree.wrap())
                } catch (ex: Throwable) {
                    LOG.error(ex) { "Exception in listener" }
                }
            }
        }
    }
    override fun addListener(l: IBranchListener) {
        // Might be unnecessary, because the purpose of this class is to let the IncrementalEngine handle changes.
        runSynchronized(branchListeners) {
            if (!branchListeners.contains(l)) {
                if (branchListeners.isEmpty()) {
                    branch.addListener(forwardingBranchListener)
                }
                branchListeners.add(l)
            }
        }
    }

    override fun removeListener(l: IBranchListener) {
        runSynchronized(branchListeners) {
            if (branchListeners.contains(l)) {
                branchListeners.remove(l)
                if (branchListeners.isEmpty()) {
                    branch.removeListener(forwardingBranchListener)
                }
            }
        }
    }

    private fun ITree.wrap(): IncrementalTree = IncrementalTree(this)

    abstract inner class IncrementalTransaction<TransactionT : ITransaction>(val transaction: TransactionT) : ITransaction {
        override val branch: IBranch get() = this@IncrementalBranch

        override fun containsNode(nodeId: Long): Boolean {
            accessed(UnclassifiedNodeDependency(this@IncrementalBranch, nodeId))
            return tree.containsNode(nodeId)
        }

        override fun getConcept(nodeId: Long): IConcept? {
            accessed(ConceptDependency(this@IncrementalBranch, nodeId))
            return transaction.getConcept(nodeId)
        }

        override fun getConceptReference(nodeId: Long): IConceptReference? {
            accessed(ConceptDependency(this@IncrementalBranch, nodeId))
            return transaction.getConceptReference(nodeId)
        }

        override fun getParent(nodeId: Long): Long {
            accessed(ContainmentDependency(this@IncrementalBranch, nodeId))
            return transaction.getParent(nodeId)
        }

        override fun getRole(nodeId: Long): String? {
            accessed(ContainmentDependency(this@IncrementalBranch, nodeId))
            return transaction.getRole(nodeId)
        }

        override fun getProperty(nodeId: Long, role: String): String? {
            accessed(PropertyDependency(this@IncrementalBranch, nodeId, role))
            return transaction.getProperty(nodeId, role)
        }

        override fun getReferenceTarget(sourceId: Long, role: String): INodeReference? {
            accessed(ReferenceDependency(this@IncrementalBranch, sourceId, role))
            return transaction.getReferenceTarget(sourceId, role)
        }

        override fun getChildren(parentId: Long, role: String?): Iterable<Long> {
            accessed(ChildrenDependency(this@IncrementalBranch, parentId, role))
            return transaction.getChildren(parentId, role)
        }

        override fun getAllChildren(parentId: Long): Iterable<Long> {
            accessed(AllChildrenDependency(this@IncrementalBranch, parentId))
            return transaction.getAllChildren(parentId)
        }

        override fun getReferenceRoles(sourceId: Long): Iterable<String> {
            accessed(AllReferencesDependency(this@IncrementalBranch, sourceId))
            return transaction.getReferenceRoles(sourceId)
        }

        override fun getPropertyRoles(sourceId: Long): Iterable<String> {
            accessed(AllPropertiesDependency(this@IncrementalBranch, sourceId))
            return transaction.getPropertyRoles(sourceId)
        }

        override fun getUserObject(key: Any): Any? {
            // intentionally no tracking for user objects
            return transaction.getUserObject(key)
        }

        override fun putUserObject(key: Any, value: Any?) {
            // intentionally no tracking for user objects
            transaction.putUserObject(key, value)
        }
    }

    inner class IncrementalReadTransaction(transaction: IReadTransaction) : IncrementalTransaction<IReadTransaction>(transaction), IReadTransaction {
        override val tree: ITree
            get() = IncrementalTree(transaction.tree)
    }

    inner class IncrementalWriteTransaction(transaction: IWriteTransaction) : IncrementalTransaction<IWriteTransaction>(transaction), IWriteTransaction {
        override var tree: ITree
            get() = IncrementalTree(transaction.tree)
            set(value) {
                val oldTree = transaction.tree
                transaction.tree = if (value is IncrementalTree) value.tree else value
                createEventsForTreeChange(oldTree, value)
            }
        override fun setProperty(nodeId: Long, role: String, value: String?) {
            transaction.setProperty(nodeId, role, value)
            modified(PropertyDependency(this@IncrementalBranch, nodeId, role))
        }

        override fun setReferenceTarget(sourceId: Long, role: String, target: INodeReference?) {
            transaction.setReferenceTarget(sourceId, role, target)
            modified(ReferenceDependency(this@IncrementalBranch, sourceId, role))
        }

        override fun moveChild(newParentId: Long, newRole: String?, newIndex: Int, childId: Long) {
            val oldParentId = transaction.getParent(childId)
            val oldRole = transaction.getRole(childId)
            transaction.moveChild(newParentId, newRole, newIndex, childId)
            modified(ChildrenDependency(this@IncrementalBranch, oldParentId, oldRole))
            modified(ChildrenDependency(this@IncrementalBranch, newParentId, newRole))
            modified(ContainmentDependency(this@IncrementalBranch, childId))
        }

        override fun addNewChild(parentId: Long, role: String?, index: Int, concept: IConcept?): Long {
            val childId = transaction.addNewChild(parentId, role, index, concept)
            modified(ChildrenDependency(this@IncrementalBranch, parentId, role))
            modified(UnclassifiedNodeDependency(this@IncrementalBranch, childId)) // see .containsNode
            return childId
        }

        override fun addNewChild(parentId: Long, role: String?, index: Int, concept: IConceptReference?): Long {
            val childId = transaction.addNewChild(parentId, role, index, concept)
            modified(ChildrenDependency(this@IncrementalBranch, parentId, role))
            modified(UnclassifiedNodeDependency(this@IncrementalBranch, childId)) // see .containsNode
            return childId
        }

        override fun addNewChildren(
            parentId: Long,
            role: String?,
            index: Int,
            concepts: Array<IConceptReference?>,
        ): LongArray {
            val childIds = transaction.addNewChildren(parentId, role, index, concepts)
            modified(ChildrenDependency(this@IncrementalBranch, parentId, role))
            childIds.forEach {
                modified(UnclassifiedNodeDependency(this@IncrementalBranch, it)) // see .containsNode
            }
            return childIds
        }

        override fun addNewChildren(
            parentId: Long,
            role: String?,
            index: Int,
            childIds: LongArray,
            concepts: Array<IConceptReference?>,
        ) {
            transaction.addNewChildren(parentId, role, index, childIds, concepts)
            modified(ChildrenDependency(this@IncrementalBranch, parentId, role))
            childIds.forEach {
                modified(UnclassifiedNodeDependency(this@IncrementalBranch, it)) // see .containsNode
            }
        }

        override fun addNewChild(parentId: Long, role: String?, index: Int, childId: Long, concept: IConcept?) {
            transaction.addNewChild(parentId, role, index, childId, concept)
            modified(ChildrenDependency(this@IncrementalBranch, parentId, role))
            modified(UnclassifiedNodeDependency(this@IncrementalBranch, childId)) // see .containsNode
        }

        override fun addNewChild(
            parentId: Long,
            role: String?,
            index: Int,
            childId: Long,
            concept: IConceptReference?,
        ) {
            transaction.addNewChild(parentId, role, index, childId, concept)
            modified(ChildrenDependency(this@IncrementalBranch, parentId, role))
            modified(UnclassifiedNodeDependency(this@IncrementalBranch, childId)) // see .containsNode
        }

        override fun setConcept(nodeId: Long, concept: IConceptReference?) {
            transaction.setConcept(nodeId, concept)
            modified(ConceptDependency(this@IncrementalBranch, nodeId))
        }

        override fun deleteNode(nodeId: Long) {
            val oldParentId = transaction.getParent(nodeId)
            val oldRole = transaction.getRole(nodeId)
            transaction.deleteNode(nodeId)
            modified(ChildrenDependency(this@IncrementalBranch, oldParentId, oldRole))
            modified(ContainmentDependency(this@IncrementalBranch, nodeId))
            modified(UnclassifiedNodeDependency(this@IncrementalBranch, nodeId)) // see .containsNode
        }
    }

    inner class IncrementalTree(val tree: ITree) : ITree {
        override fun asObject() = tree.asObject()

        override fun asAsyncTree(): IAsyncMutableTree {
            throw UnsupportedOperationException("Dependency recording not supported yet for IAsyncTree")
            // return tree.asAsyncTree()
        }

        override fun usesRoleIds(): Boolean {
            return tree.usesRoleIds()
        }

        override fun getId(): String {
            return tree.getId()
        }

        override fun visitChanges(oldVersion: ITree, visitor: ITreeChangeVisitor) {
            tree.visitChanges(if (oldVersion is IncrementalTree) oldVersion.tree else oldVersion, visitor)
        }

        override fun containsNode(nodeId: Long): Boolean {
            accessed(UnclassifiedNodeDependency(this@IncrementalBranch, nodeId))
            return tree.containsNode(nodeId)
        }

        override fun getProperty(nodeId: Long, role: String): String? {
            accessed(PropertyDependency(this@IncrementalBranch, nodeId, role))
            return tree.getProperty(nodeId, role)
        }

        override fun getChildren(parentId: Long, role: String?): Iterable<Long> {
            accessed(ChildrenDependency(this@IncrementalBranch, parentId, role))
            return tree.getChildren(parentId, role)
        }

        override fun getConcept(nodeId: Long): IConcept? {
            accessed(ConceptDependency(this@IncrementalBranch, nodeId))
            return tree.getConcept(nodeId)
        }

        override fun getConceptReference(nodeId: Long): IConceptReference? {
            accessed(ConceptDependency(this@IncrementalBranch, nodeId))
            return tree.getConceptReference(nodeId)
        }

        override fun getParent(nodeId: Long): Long {
            accessed(ContainmentDependency(this@IncrementalBranch, nodeId))
            return tree.getParent(nodeId)
        }

        override fun getRole(nodeId: Long): String? {
            accessed(ContainmentDependency(this@IncrementalBranch, nodeId))
            return tree.getRole(nodeId)
        }

        override fun setProperty(nodeId: Long, role: String, value: String?): ITree {
            // No modification event, because the branch doesn't change
            return tree.setProperty(nodeId, role, value).wrap()
        }

        override fun getReferenceTarget(sourceId: Long, role: String): INodeReference? {
            accessed(ReferenceDependency(this@IncrementalBranch, sourceId, role))
            return tree.getReferenceTarget(sourceId, role)
        }

        override fun setReferenceTarget(sourceId: Long, role: String, target: INodeReference?): ITree {
            // No modification event, because the branch doesn't change
            return tree.setReferenceTarget(sourceId, role, target).wrap()
        }

        override fun getReferenceRoles(sourceId: Long): Iterable<String> {
            accessed(AllReferencesDependency(this@IncrementalBranch, sourceId))
            return tree.getReferenceRoles(sourceId)
        }

        override fun getPropertyRoles(sourceId: Long): Iterable<String> {
            accessed(AllPropertiesDependency(this@IncrementalBranch, sourceId))
            return tree.getPropertyRoles(sourceId)
        }

        override fun getChildRoles(sourceId: Long): Iterable<String?> {
            accessed(AllChildrenDependency(this@IncrementalBranch, sourceId))
            return tree.getChildRoles(sourceId)
        }

        override fun getAllChildren(parentId: Long): Iterable<Long> {
            accessed(AllChildrenDependency(this@IncrementalBranch, parentId))
            return tree.getAllChildren(parentId)
        }

        override fun moveChild(newParentId: Long, newRole: String?, newIndex: Int, childId: Long): ITree {
            // No modification event, because the branch doesn't change
            return tree.moveChild(newParentId, newRole, newIndex, childId).wrap()
        }

        override fun addNewChild(parentId: Long, role: String?, index: Int, childId: Long, concept: IConcept?): ITree {
            // No modification event, because the branch doesn't change
            return tree.addNewChild(parentId, role, index, childId, concept).wrap()
        }

        override fun addNewChild(
            parentId: Long,
            role: String?,
            index: Int,
            childId: Long,
            concept: IConceptReference?,
        ): ITree {
            // No modification event, because the branch doesn't change
            return tree.addNewChild(parentId, role, index, childId, concept).wrap()
        }

        override fun addNewChildren(
            parentId: Long,
            role: String?,
            index: Int,
            newIds: LongArray,
            concepts: Array<IConcept?>,
        ): ITree {
            // No modification event, because the branch doesn't change
            return tree.addNewChildren(parentId, role, index, newIds, concepts).wrap()
        }

        override fun addNewChildren(
            parentId: Long,
            role: String?,
            index: Int,
            newIds: LongArray,
            concepts: Array<IConceptReference?>,
        ): ITree {
            // No modification event, because the branch doesn't change
            return tree.addNewChildren(parentId, role, index, newIds, concepts).wrap()
        }

        override fun setConcept(nodeId: Long, concept: IConceptReference?): ITree {
            // No modification event, because the branch doesn't change
            return tree.setConcept(nodeId, concept)
        }

        override fun deleteNode(nodeId: Long): ITree {
            // No modification event, because the branch doesn't change
            return tree.deleteNode(nodeId).wrap()
        }

        override fun deleteNodes(nodeIds: LongArray): ITree {
            // No modification event, because the branch doesn't change
            return tree.deleteNodes(nodeIds).wrap()
        }
    }
}

abstract class DependencyBase : IStateVariableReference<Unit> {
    override fun read() {
    }
}

data class BranchDependency(val branch: IBranch) : IStateVariableReference<IBranch> {
    override fun getGroup(): IStateVariableGroup? = null
    override fun read(): IBranch = branch
}

/**
 * Catch-all dependency for changes to a node.
 * It's the group dependency for a single node.
 * Also used directly when a node is added/deleted as a whole.
 */
data class UnclassifiedNodeDependency(val branch: IBranch, val nodeId: Long) : DependencyBase() {
    override fun getGroup(): IStateVariableGroup? {
        return try {
            branch.computeReadT { if (it.containsNode(nodeId)) it.getParent(nodeId) else 0L }
                .let { parent -> if (parent == 0L) null else UnclassifiedNodeDependency(branch, parent) }
        } catch (ex: org.modelix.datastructures.model.NodeNotFoundException) {
            BranchDependency(branch)
        }
    }

    override fun read() {
    }
}

/**
 * Dependency for a single property change.
 *
 * @see AllPropertiesDependency
 */
data class PropertyDependency(val branch: IBranch, val nodeId: Long, val role: String) : DependencyBase() {
    override fun getGroup() = AllPropertiesDependency(branch, nodeId)
}

/**
 * Dependency for a single reference change.
 *
 * @see AllReferencesDependency
 */
data class ReferenceDependency(val branch: IBranch, val nodeId: Long, val role: String) : DependencyBase() {
    override fun getGroup() = AllReferencesDependency(branch, nodeId)
}

/**
 * Dependency for a single child role change.
 *
 * @see AllChildrenDependency
 */
data class ChildrenDependency(val branch: IBranch, val nodeId: Long, val role: String?) : DependencyBase() {
    override fun getGroup() = AllChildrenDependency(branch, nodeId)
}

/**
 * Dependency for any child role changes.
 *
 * @see ChildrenDependency
 */
data class AllChildrenDependency(val branch: IBranch, val nodeId: Long) : DependencyBase() {
    override fun getGroup() = UnclassifiedNodeDependency(branch, nodeId)
}

/**
 * Dependency for any reference changes.
 *
 * @see ReferenceDependency
 */
data class AllReferencesDependency(val branch: IBranch, val nodeId: Long) : DependencyBase() {
    override fun getGroup() = UnclassifiedNodeDependency(branch, nodeId)
}

/**
 * Dependency for any property changes.
 *
 * @see PropertyDependency
 */
data class AllPropertiesDependency(val branch: IBranch, val nodeId: Long) : DependencyBase() {
    override fun getGroup() = UnclassifiedNodeDependency(branch, nodeId)
}

/**
 * Dependency for parent or role in parent changes.
 */
data class ContainmentDependency(val branch: IBranch, val nodeId: Long) : DependencyBase() {
    override fun getGroup() = UnclassifiedNodeDependency(branch, nodeId)
}

/**
 * Dependency for concept changes.
 */
data class ConceptDependency(val branch: IBranch, val nodeId: Long) : DependencyBase() {
    override fun getGroup(): IStateVariableGroup = UnclassifiedNodeDependency(branch, nodeId)
}

fun IBranch.withIncrementalComputationSupport() = IncrementalBranch(this)
