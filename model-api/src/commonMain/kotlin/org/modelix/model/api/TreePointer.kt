package org.modelix.model.api

/**
 * A non thread safe branch without real transactions.
 * Provides better performance for temporary branches that are not shared.
 */
class TreePointer(private var tree_: ITree, val idGenerator: IIdGenerator = IdGeneratorDummy()) : IBranch, IWriteTransaction, IReadTransaction {
    private val userObjects = HashMap<Any, Any?>()
    private val listeners = LinkedHashSet<IBranchListener>()
    override fun getUserObject(key: Any) = userObjects[key]

    override fun putUserObject(key: Any, value: Any?) {
        userObjects[key] = value
    }

    override var tree: ITree
        get() = tree_
        set(value) {
            val oldTree = tree
            tree_ = value
            listeners.forEach { it.treeChanged(oldTree, value) }
        }

    override fun runRead(runnable: () -> Unit) {
        computeRead(runnable)
    }

    override fun <T> computeRead(computable: () -> T): T {
        return computable()
    }

    override fun runWrite(runnable: () -> Unit) {
        computeWrite(runnable)
    }

    override fun <T> computeWrite(computable: () -> T): T {
        return computable()
    }

    override fun canRead(): Boolean {
        return true
    }

    override fun canWrite(): Boolean {
        return true
    }

    override val transaction: ITransaction
        get() = this
    override val readTransaction: IReadTransaction
        get() = this
    override val writeTransaction: IWriteTransaction
        get() = this

    override fun addListener(l: IBranchListener) {
        listeners.add(l)
    }

    override fun removeListener(l: IBranchListener) {
        listeners.remove(l)
    }

    override val branch: IBranch
        get() = this

    override fun containsNode(nodeId: Long) = tree.containsNode(nodeId)

    override fun getConcept(nodeId: Long) = tree.getConcept(nodeId)

    override fun getConceptReference(nodeId: Long): IConceptReference? = tree.getConceptReference(nodeId)

    override fun getParent(nodeId: Long) = tree.getParent(nodeId)

    override fun getRole(nodeId: Long) = tree.getRole(nodeId)

    override fun getProperty(nodeId: Long, role: String) = tree.getProperty(nodeId, role)

    override fun getReferenceTarget(sourceId: Long, role: String) = tree.getReferenceTarget(sourceId, role)

    override fun getChildren(parentId: Long, role: String?) = tree.getChildren(parentId, role)

    override fun getAllChildren(parentId: Long) = tree.getAllChildren(parentId)

    override fun getReferenceRoles(sourceId: Long) = tree.getReferenceRoles(sourceId)

    override fun getPropertyRoles(sourceId: Long) = tree.getPropertyRoles(sourceId)

    override fun setProperty(nodeId: Long, role: String, value: String?) {
        tree = tree.setProperty(nodeId, role, value)
    }

    override fun setReferenceTarget(sourceId: Long, role: String, target: INodeReference?) {
        tree = tree.setReferenceTarget(sourceId, role, target)
    }

    override fun moveChild(newParentId: Long, newRole: String?, newIndex: Int, childId: Long) {
        tree = tree.moveChild(newParentId, newRole, newIndex, childId)
    }

    override fun addNewChild(parentId: Long, role: String?, index: Int, concept: IConcept?): Long {
        val childId = idGenerator.generate()
        addNewChild(parentId, role, index, childId, concept)
        return childId
    }

    override fun addNewChild(parentId: Long, role: String?, index: Int, concept: IConceptReference?): Long {
        val childId = idGenerator.generate()
        addNewChild(parentId, role, index, childId, concept)
        return childId
    }

    override fun addNewChild(parentId: Long, role: String?, index: Int, childId: Long, concept: IConcept?) {
        tree = tree.addNewChild(parentId, role, index, childId, concept)
    }

    override fun addNewChild(parentId: Long, role: String?, index: Int, childId: Long, concept: IConceptReference?) {
        tree = tree.addNewChild(parentId, role, index, childId, concept)
    }

    override fun addNewChildren(
        parentId: Long,
        role: String?,
        index: Int,
        concepts: Array<IConceptReference?>,
    ): LongArray {
        val newIds = concepts.map { idGenerator.generate() }.toLongArray()
        addNewChildren(parentId, role, index, newIds, concepts)
        return newIds
    }

    override fun addNewChildren(
        parentId: Long,
        role: String?,
        index: Int,
        childIds: LongArray,
        concepts: Array<IConceptReference?>,
    ) {
        tree = tree.addNewChildren(parentId, role, index, childIds, concepts)
    }

    override fun setConcept(nodeId: Long, concept: IConceptReference?) {
        tree = tree.setConcept(nodeId, concept)
    }

    override fun deleteNode(nodeId: Long) {
        tree = tree.deleteNode(nodeId)
    }

    override fun getId(): String {
        return tree.getId() ?: throw UnsupportedOperationException()
    }
}
