package org.modelix.editor

import org.modelix.incremental.DependencyTracking
import org.modelix.incremental.IStateVariableGroup
import org.modelix.incremental.IStateVariableReference
import org.modelix.model.api.*

class IncrementalBranch(val branch: IBranch) : IBranch {

    init {
        branch.addListener(object : IBranchListener {
            override fun treeChanged(oldTree: ITree?, newTree: ITree) {
                if (oldTree != null) {
                    newTree.visitChanges(oldTree, object : ITreeChangeVisitor {
                        override fun containmentChanged(nodeId: Long) {
                            modified(ContainmentDependency(this@IncrementalBranch, nodeId))
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
                    })
                }
            }
        })
    }

    fun accessed(dep: IStateVariableReference<*>) {
        DependencyTracking.accessed(dep)
    }

    fun modified(dep: IStateVariableReference<*>) {
        DependencyTracking.modified(dep)
    }

    override fun getId(): String = branch.getId()

    override fun runReadT(f: (IReadTransaction) -> Unit) {
        branch.runReadT { f(IncrementalReadTransaction(it)) }
    }

    override fun <T> computeReadT(computable: (IReadTransaction) -> T): T {
        return branch.computeReadT { computable(IncrementalReadTransaction(it)) }
    }

    override fun runWriteT(f: (IWriteTransaction) -> Unit) {
        branch.runWriteT { f(IncrementalWriteTransaction(it)) }
    }

    override fun <T> computeWriteT(computable: (IWriteTransaction) -> T): T {
        return branch.computeWriteT { computable(IncrementalWriteTransaction(it)) }
    }

    override fun runRead(runnable: () -> Unit) {
        return branch.runRead(runnable)
    }

    override fun <T> computeRead(computable: () -> T): T {
        return branch.computeRead(computable)
    }

    override fun runWrite(runnable: () -> Unit) {
        branch.runWrite(runnable)
    }

    override fun <T> computeWrite(computable: () -> T): T {
        return branch.computeWrite(computable)
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

    override fun addListener(l: IBranchListener) {
        TODO("Not yet implemented")
        // Might be unnecessary, because the purpose of this class is to let the IncrementalEngine handle changes.
    }

    override fun removeListener(l: IBranchListener) {
        TODO("Not yet implemented")
    }

    abstract inner class IncrementalTransaction<TransactionT : ITransaction>(val transaction: TransactionT) : ITransaction {
        override val branch: IBranch get() = this@IncrementalBranch

        override fun containsNode(nodeId: Long): Boolean {
            accessed(UnclassifiedNodeDependency(this@IncrementalBranch, nodeId))
            return tree.containsNode(nodeId)
        }

        override fun getConcept(nodeId: Long): IConcept? {
            accessed(UnclassifiedNodeDependency(this@IncrementalBranch, nodeId))
            return transaction.getConcept(nodeId)
        }

        override fun getConceptReference(nodeId: Long): IConceptReference? {
            accessed(UnclassifiedNodeDependency(this@IncrementalBranch, nodeId))
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
            set(value) { if (value is IncrementalTree) transaction.tree = value.tree else tree }
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
            return childId
        }

        override fun addNewChild(parentId: Long, role: String?, index: Int, concept: IConceptReference?): Long {
            val childId = transaction.addNewChild(parentId, role, index, concept)
            modified(ChildrenDependency(this@IncrementalBranch, parentId, role))
            return childId
        }

        override fun addNewChild(parentId: Long, role: String?, index: Int, childId: Long, concept: IConcept?) {
            transaction.addNewChild(parentId, role, index, childId, concept)
            modified(ChildrenDependency(this@IncrementalBranch, parentId, role))
        }

        override fun addNewChild(
            parentId: Long,
            role: String?,
            index: Int,
            childId: Long,
            concept: IConceptReference?
        ) {
            transaction.addNewChild(parentId, role, index, childId, concept)
            modified(ChildrenDependency(this@IncrementalBranch, parentId, role))
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
        override fun getId(): String? {
            return tree.getId()
        }

        override fun visitChanges(oldVersion: ITree, visitor: ITreeChangeVisitor) {
            TODO("Not yet implemented")
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
            accessed(UnclassifiedNodeDependency(this@IncrementalBranch, nodeId))
            return tree.getConcept(nodeId)
        }

        override fun getConceptReference(nodeId: Long): IConceptReference? {
            accessed(UnclassifiedNodeDependency(this@IncrementalBranch, nodeId))
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
            TODO("Not yet implemented")
        }

        override fun getReferenceTarget(sourceId: Long, role: String): INodeReference? {
            accessed(ReferenceDependency(this@IncrementalBranch, sourceId, role))
            return tree.getReferenceTarget(sourceId, role)
        }

        override fun setReferenceTarget(sourceId: Long, role: String, target: INodeReference?): ITree {
            TODO("Not yet implemented")
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
            TODO("Not yet implemented")
        }

        override fun addNewChild(parentId: Long, role: String?, index: Int, childId: Long, concept: IConcept?): ITree {
            TODO("Not yet implemented")
        }

        override fun addNewChild(
            parentId: Long,
            role: String?,
            index: Int,
            childId: Long,
            concept: IConceptReference?
        ): ITree {
            TODO("Not yet implemented")
        }

        override fun addNewChildren(
            parentId: Long,
            role: String?,
            index: Int,
            newIds: LongArray,
            concepts: Array<IConcept?>
        ): ITree {
            TODO("Not yet implemented")
        }

        override fun addNewChildren(
            parentId: Long,
            role: String?,
            index: Int,
            newIds: LongArray,
            concepts: Array<IConceptReference?>
        ): ITree {
            TODO("Not yet implemented")
        }

        override fun deleteNode(nodeId: Long): ITree {
            TODO("Not yet implemented")
        }

        override fun deleteNodes(nodeIds: LongArray): ITree {
            TODO("Not yet implemented")
        }
    }
}

abstract class DependencyBase : IStateVariableReference<Unit> {
    override fun read() {
    }
}

data class UnclassifiedNodeDependency(val branch: IBranch, val nodeId: Long) : DependencyBase() {
    override fun getGroup(): IStateVariableGroup? {
        return branch.computeReadT { it.getParent(nodeId) }
            .let { parent -> if (parent == 0L) null else UnclassifiedNodeDependency(branch, parent) }
    }

    override fun read() {
    }
}

data class PropertyDependency(val branch: IBranch, val nodeId: Long, val role: String) : DependencyBase() {
    override fun getGroup() = AllPropertiesDependency(branch, nodeId)
}

data class ReferenceDependency(val branch: IBranch, val nodeId: Long, val role: String) : DependencyBase() {
    override fun getGroup() = AllReferencesDependency(branch, nodeId)
}

data class ChildrenDependency(val branch: IBranch, val nodeId: Long, val role: String?) : DependencyBase() {
    override fun getGroup() = AllChildrenDependency(branch, nodeId)
}

data class AllChildrenDependency(val branch: IBranch, val nodeId: Long) : DependencyBase() {
    override fun getGroup() = UnclassifiedNodeDependency(branch, nodeId)
}

data class AllReferencesDependency(val branch: IBranch, val nodeId: Long) : DependencyBase() {
    override fun getGroup() = UnclassifiedNodeDependency(branch, nodeId)
}

data class AllPropertiesDependency(val branch: IBranch, val nodeId: Long) : DependencyBase() {
    override fun getGroup() = UnclassifiedNodeDependency(branch, nodeId)
}

data class ContainmentDependency(val branch: IBranch, val nodeId: Long) : DependencyBase() {
    override fun getGroup() = UnclassifiedNodeDependency(branch, nodeId)
}