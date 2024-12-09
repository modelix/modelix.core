package org.modelix.model.api

class WriteTransaction(_tree: ITree, branch: IBranch, idGenerator: IIdGenerator) : Transaction(branch), IWriteTransaction {
    override var tree: ITree = _tree
        set(value) {
            checkNotClosed()
            field = value
        }
    private var closed = false
    protected var idGenerator: IIdGenerator
    fun close() {
        closed = true
    }

    protected fun checkNotClosed() {
        check(!closed) { "Transaction is already closed" }
    }

//    override fun getTree(): ITree? {
//        return tree
//    }
//
//    override fun setTree(newTree: ITree?) {
//        checkNotClosed()
//        tree = newTree
//    }

    override fun setProperty(nodeId: Long, role: String, value: String?) {
        checkNotClosed()
        tree = tree.setProperty(nodeId, role, value)
    }

    override fun setReferenceTarget(sourceId: Long, role: String, target: INodeReference?) {
        checkNotClosed()
        tree = tree.setReferenceTarget(sourceId, role, target)
    }

    override fun moveChild(newParentId: Long, newRole: String?, newIndex: Int, childId: Long) {
        checkNotClosed()
        tree = tree.moveChild(newParentId, newRole, newIndex, childId)
    }

    override fun addNewChild(parentId: Long, role: String?, index: Int, concept: IConcept?): Long {
        checkNotClosed()
        val newId = idGenerator.generate()
        addNewChild(parentId, role, index, newId, concept)
        return newId
    }

    override fun addNewChild(parentId: Long, role: String?, index: Int, concept: IConceptReference?): Long {
        checkNotClosed()
        val newId = idGenerator.generate()
        addNewChild(parentId, role, index, newId, concept)
        return newId
    }

    override fun addNewChild(parentId: Long, role: String?, index: Int, childId: Long, concept: IConcept?) {
        checkNotClosed()
        tree = tree.addNewChild(parentId, role, index, childId, concept)
    }

    override fun addNewChild(parentId: Long, role: String?, index: Int, childId: Long, concept: IConceptReference?) {
        checkNotClosed()
        tree = tree.addNewChild(parentId, role, index, childId, concept)
    }

    override fun addNewChildren(parentId: Long, role: String?, index: Int, childIds: LongArray, concepts: Array<IConceptReference?>) {
        checkNotClosed()
        tree = tree.addNewChildren(parentId, role, index, childIds, concepts)
    }

    override fun addNewChildren(parentId: Long, role: String?, index: Int, concepts: Array<IConceptReference?>): LongArray {
        checkNotClosed()
        val newIds = concepts.map { idGenerator.generate() }.toLongArray()
        addNewChildren(parentId, role, index, newIds, concepts)
        return newIds
    }

    override fun setConcept(nodeId: Long, concept: IConceptReference?) {
        checkNotClosed()
        tree = tree.setConcept(nodeId, concept)
    }

    override fun deleteNode(nodeId: Long) {
        checkNotClosed()
        tree.getAllChildren(nodeId).forEach { deleteNode(it) }
        tree = tree.deleteNode(nodeId)
    }

    init {
        this.tree = tree
        this.idGenerator = idGenerator
    }
}
