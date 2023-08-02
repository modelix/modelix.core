/*
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *  http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

package org.modelix.model.api

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapNotNull
import org.modelix.model.area.ContextArea
import org.modelix.model.area.IArea
import org.modelix.model.area.PArea
import kotlin.coroutines.coroutineContext

open class PNodeAdapter(val nodeId: Long, val branch: IBranch) : INode, INodeEx {

    init {
        require(nodeId != 0L, { "Invalid node 0" })
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
        return PNodeAdapter(branch.writeTransaction.addNewChild(nodeId, role, index, concept), branch)
    }

    override val allChildren: Iterable<INode>
        get() {
            notifyAccess()
            return branch.transaction.getAllChildren(nodeId)
                .map { id: Long -> wrap(id) ?: throw RuntimeException("Unexpected null child") }
        }

    override fun getChildren(role: String?): Iterable<INode> {
        notifyAccess()
        return branch.transaction.getChildren(nodeId, role)
            .map { id: Long -> wrap(id) ?: throw RuntimeException("Unexpected null child") }
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
            throw RuntimeException("Issue getting concept for $nodeId in branch $branch", e)
        }
    }

    override val parent: INode?
        get() {
            notifyAccess()
            val parent = branch.transaction.getParent(nodeId)
            return if (parent == 0L) null else wrap(parent)
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
        return tryResolveNodeRef(targetRef)
    }

    private fun tryResolveNodeRef(targetRef: INodeReference): INode? {
        return if (targetRef is PNodeReference) {
            targetRef.resolveIn(PArea(branch)!!)
        } else {
            val area = ContextArea.CONTEXT_VALUE.getValue()
                ?: throw RuntimeException(IArea::class.simpleName + " not available")
            targetRef.resolveIn(area!!)
        }
    }

    private suspend fun resolveNodeRefInCoroutine(targetRef: INodeReference): INode {
        return if (targetRef is PNodeReference) {
            targetRef.resolveIn(PArea(branch)!!)
        } else {
            val scope = coroutineContext[INodeResolutionScope]
                ?: throw IllegalStateException("INodeResolutionScope not set")
            targetRef.resolveIn(scope)
        } ?: throw RuntimeException("Failed to resolve node: $targetRef")
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
        return getTree().getAllChildrenAsFlow(nodeId).map { wrap(it)!! }
    }

    override fun getDescendantsAsFlow(includeSelf: Boolean): Flow<INode> {
        return getTree().getDescendantsAsFlow(nodeId, includeSelf).map { wrap(it)!! }
    }

    override fun getAllReferenceTargetsAsFlow(): Flow<Pair<IReferenceLink, INode>> {
        return getAllReferenceTargetRefsAsFlow().map { it.first to resolveNodeRefInCoroutine(it.second) }
    }

    override fun getAllReferenceTargetRefsAsFlow(): Flow<Pair<IReferenceLink, INodeReference>> {
        return getTree().getAllReferenceTargetsAsFlow(nodeId).map { this.resolveReferenceLinkOrFallback(it.first) to it.second }
    }

    override fun getChildrenAsFlow(role: IChildLink): Flow<INode> {
        val tree = getTree()
        return tree.getChildrenAsFlow(nodeId, role.key(tree)).map { wrap(it)!! }
    }

    override fun getParentAsFlow(): Flow<INode> {
        return getTree().getParentAsFlow(nodeId).map { wrap(it)!! }
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

    private fun wrap(id: Long): INode? {
        return Companion.wrap(id, this.branch)
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
