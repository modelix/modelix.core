package org.modelix.model.api

abstract class Transaction(override val branch: IBranch) : ITransaction {
    private val userObjects = HashMap<Any, Any?>()
    override fun getUserObject(key: Any) = userObjects[key]

    override fun putUserObject(key: Any, value: Any?) {
        userObjects[key] = value
    }

    override fun containsNode(nodeId: Long): Boolean = tree.containsNode(nodeId)
    override fun getConcept(nodeId: Long): IConcept? = tree.getConcept(nodeId)
    override fun getConceptReference(nodeId: Long): IConceptReference? = tree.getConceptReference(nodeId)
    override fun getParent(nodeId: Long): Long = tree.getParent(nodeId)
    override fun getRole(nodeId: Long): String? = tree.getRole(nodeId)
    override fun getProperty(nodeId: Long, role: String): String? = tree.getProperty(nodeId, role)
    override fun getReferenceTarget(sourceId: Long, role: String): INodeReference? {
        val target = tree.getReferenceTarget(sourceId, role)
        return if (target is LocalPNodeReference) {
            PNodeReference(target.id, branch.getId())
        } else {
            target
        }
    }
    override fun getChildren(parentId: Long, role: String?): Iterable<Long> = tree.getChildren(parentId, role)
    override fun getAllChildren(parentId: Long): Iterable<Long> = tree.getAllChildren(parentId)
    override fun getReferenceRoles(sourceId: Long): Iterable<String> = tree.getReferenceRoles(sourceId)
    override fun getPropertyRoles(sourceId: Long): Iterable<String> = tree.getPropertyRoles(sourceId)
}
