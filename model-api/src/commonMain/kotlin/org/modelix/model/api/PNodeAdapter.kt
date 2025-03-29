package org.modelix.model.api

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapNotNull
import org.modelix.model.api.async.AsyncNode
import org.modelix.model.api.async.IAsyncNode
import org.modelix.model.api.async.INodeWithAsyncSupport
import org.modelix.model.api.async.asAsyncNode
import org.modelix.model.area.PArea

open class PNodeAdapter(val nodeId: Long, val branch: IBranch) :
    INode,
    INodeEx,
    IReplaceableNode,
    INodeWithAsyncSupport {

    init {
        require(nodeId != 0L, { "Invalid node 0" })
    }

    override fun getAsyncNode(): IAsyncNode {
        return AsyncNode(this, nodeId, { branch.transaction.tree.asAsyncTree() }, { createAdapter(it).asAsyncNode() })
    }

    private fun getTree(): ITree = branch.transaction.tree

    override fun getArea(): PArea = PArea(branch)

    protected fun unwrap(node: INode?): Long {
        if (node == null) {
            return 0
        }
        if (node !is PNodeAdapter) {
            throw RuntimeException("Not a " + PNodeAdapter::class.simpleName + ": " + node)
        }
        if (node.branch != branch) {
            throw RuntimeException("Node belongs to a different branch. Expected $branch but was ${node.branch}")
        }
        return node.nodeId
    }

    protected fun notifyAccess() {
        // TODO
//    DependencyBroadcaster.INSTANCE.dependencyAccessed(new PNodeDependency(branch, nodeId));
    }

    override fun usesRoleIds() = branch.transaction.tree.usesRoleIds()

    override fun moveChild(role: String?, index: Int, child: INode) {
        if (child !is PNodeAdapter) {
            throw RuntimeException(child::class.simpleName + " cannot be moved to " + this::class.simpleName)
        }
        if (child.branch != this.branch) {
            throw RuntimeException("child in branch ${child.branch.getId()} cannot be moved to parent in branch ${branch.getId()}")
        }
        branch.writeTransaction.moveChild(nodeId, role, index, child.nodeId)
    }

    override fun addNewChild(role: String?, index: Int, concept: IConcept?): INode {
        return createAdapter(branch.writeTransaction.addNewChild(nodeId, role, index, concept))
    }

    override fun addNewChild(role: String?, index: Int, concept: IConceptReference?): INode {
        return createAdapter(branch.writeTransaction.addNewChild(nodeId, role, index, concept))
    }

    override fun addNewChildren(role: String?, index: Int, concepts: List<IConceptReference?>): List<INode> {
        return branch.writeTransaction.addNewChildren(nodeId, role, index, concepts.toTypedArray()).map {
            createAdapter(it)
        }
    }

    override fun addNewChildren(
        link: IChildLink,
        index: Int,
        concepts: List<IConceptReference?>,
    ): List<INode> {
        return branch.writeTransaction.addNewChildren(nodeId, link.key(this), index, concepts.toTypedArray()).map {
            createAdapter(it)
        }
    }

    override fun addNewChild(role: IChildLink, index: Int, concept: IConcept?): INode {
        return createAdapter(branch.writeTransaction.addNewChild(nodeId, role.key(this), index, concept))
    }

    override fun addNewChild(role: IChildLink, index: Int, concept: IConceptReference?): INode {
        return createAdapter(branch.writeTransaction.addNewChild(nodeId, role.key(this), index, concept))
    }

    override fun replaceNode(concept: ConceptReference?): INode {
        branch.writeTransaction.setConcept(nodeId, concept)
        return this
    }

    override val allChildren: Iterable<INode>
        get() {
            notifyAccess()
            return branch.transaction.getAllChildren(nodeId).map { createAdapter(it) }
        }

    override fun getChildren(role: String?): Iterable<INode> {
        notifyAccess()
        return branch.transaction.getChildren(nodeId, role).map { createAdapter(it) }
    }

    override val concept: IConcept?
        get() {
            try {
                notifyAccess()
                return branch.computeRead { branch.transaction.getConcept(nodeId) }
            } catch (e: RuntimeException) {
                throw RuntimeException("Issue getting concept for $nodeId in branch $branch", e)
            }
        }

    override fun getConceptReference(): IConceptReference? {
        try {
            notifyAccess()
            return branch.computeRead { branch.transaction.getConceptReference(nodeId) }
        } catch (e: RuntimeException) {
            throw RuntimeException("Issue getting concept for ${nodeId.toString(16)} in branch $branch", e)
        }
    }

    override val parent: INode?
        get() {
            notifyAccess()
            val parent = branch.transaction.getParent(nodeId)
            return createAdapterIfNot0(parent)
        }

    override fun getPropertyValue(role: String): String? {
        notifyAccess()
        return branch.transaction.getProperty(nodeId, role)
    }

    override val reference: INodeReference
        get() = PNodeReference(nodeId, branch.getId())

    override fun getReferenceTarget(role: String): INode? {
        notifyAccess()
        val targetRef = getReferenceTargetRef(role) ?: return null
        return tryResolveNodeRef(targetRef)?.let { createAdapter(it) }
    }

    private fun tryResolveNodeRef(targetRef: INodeReference): INode? {
        return INodeResolutionScope.runWithAdditionalScope(getArea()) {
            targetRef.resolveInCurrentContext()
        }?.let { createAdapter(it) }
    }

    private suspend fun resolveNodeRefInCoroutine(targetRef: INodeReference): INode {
        return (tryResolveNodeRef(targetRef) ?: throw RuntimeException("Failed to resolve node: $targetRef")).let { createAdapter(it) }
    }

    override fun getReferenceTargetRef(role: String): INodeReference? {
        notifyAccess()
        return branch.transaction.getReferenceTarget(nodeId, role)
    }

    override val roleInParent: String?
        get() {
            notifyAccess()
            return branch.transaction.getRole(nodeId)
        }

    override fun getContainmentLink(): IChildLink? {
        return IChildLinkReference.fromNullableUnclassifiedString(roleInParent).toLegacy()
    }

    override val isValid: Boolean
        get() {
            notifyAccess()
            return branch.transaction.containsNode(nodeId)
        }

    override fun removeChild(child: INode) {
        branch.writeTransaction.deleteNode(unwrap(child))
    }

    override fun setPropertyValue(role: String, value: String?) {
        branch.writeTransaction.setProperty(nodeId, role, value)
    }

    override fun setReferenceTarget(role: String, target: INode?) {
        branch.writeTransaction.setReferenceTarget(nodeId, role, target?.reference)
    }

    override fun setReferenceTarget(role: String, target: INodeReference?) {
        branch.writeTransaction.setReferenceTarget(nodeId, role, target)
    }

    override fun getPropertyRoles(): List<String> {
        return branch.transaction.getPropertyRoles(nodeId).toList()
    }

    override fun getReferenceRoles(): List<String> {
        return branch.transaction.getReferenceRoles(nodeId).toList()
    }

    override fun getAllChildrenAsFlow(): Flow<INode> {
        return getTree().getAllChildrenAsFlow(nodeId).map { createAdapter(it) }
    }

    override fun getDescendantsAsFlow(includeSelf: Boolean): Flow<INode> {
        return getTree().getDescendantsAsFlow(nodeId, includeSelf).map { createAdapter(it) }
    }

    override fun getAllReferenceTargetsAsFlow(): Flow<Pair<IReferenceLink, INode>> {
        return getAllReferenceTargetRefsAsFlow().map { it.first to resolveNodeRefInCoroutine(it.second) }
    }

    override fun getAllReferenceTargetRefsAsFlow(): Flow<Pair<IReferenceLink, INodeReference>> {
        return getTree().getAllReferenceTargetsAsFlow(nodeId).map { this.resolveReferenceLinkOrFallback(it.first) to it.second }
    }

    override fun getChildrenAsFlow(role: IChildLink): Flow<INode> {
        val tree = getTree()
        return tree.getChildrenAsFlow(nodeId, role.key(tree)).map { createAdapter(it) }
    }

    override fun getParentAsFlow(): Flow<INode> {
        return getTree().getParentAsFlow(nodeId).map { createAdapter(it) }
    }

    override fun getPropertyValueAsFlow(role: IProperty): Flow<String?> {
        val tree = getTree()
        return tree.getPropertyValueAsFlow(nodeId, role.key(tree))
    }

    override fun getReferenceTargetAsFlow(role: IReferenceLink): Flow<INode> {
        return getReferenceTargetRefAsFlow(role).mapNotNull { tryResolveNodeRef(it) }
    }

    override fun getReferenceTargetRefAsFlow(role: IReferenceLink): Flow<INodeReference> {
        val tree = getTree()
        return tree.getReferenceTargetAsFlow(nodeId, role.key(tree))
    }

    override fun equals(o: Any?): Boolean {
        if (this === o) {
            return true
        }
        if (o == null || this::class != o::class) {
            return false
        }
        val that = o as PNodeAdapter
        if (branch != that.branch) {
            return false
        }
        return nodeId == that.nodeId
    }

    override fun hashCode(): Int {
        var result = 0
        result = 31 * result + (branch as Any).hashCode()
        result = 31 * result + (nodeId xor (nodeId shr 32)).toInt()
        return result
    }

    override fun toString(): String {
        var concept: IConcept? = null
        try {
            concept = branch.computeRead { branch.transaction.getConcept(nodeId) }
        } catch (_: Exception) {
        }
        var str = "PNode${nodeId.toString(16)}"
        if (concept != null) {
            str += "[$concept]"
        }
        return str
    }

    protected open fun createAdapter(id: Long): INode {
        return PNodeAdapter(id, branch)
    }

    protected open fun createAdapter(node: INode): INode {
        return node
    }

    private fun createAdapterIfNot0(id: Long): INode? {
        return if (id == 0L) null else createAdapter(id)
    }

    companion object {
        fun wrap(id: Long, branch: IBranch): INode? {
            return if (id == 0L) null else PNodeAdapter(id, branch)
        }
    }

    init {
        if (this.nodeId == 0L) throw IllegalArgumentException("ID 0 not allowed")
        notifyAccess()
    }
}

fun IBranch.getNode(id: Long): INode = PNodeAdapter(id, this)
fun IBranch.getRootNode(): INode = getNode(ITree.ROOT_ID)
