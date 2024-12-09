package org.modelix.model.area

import org.modelix.model.api.IBranch
import org.modelix.model.api.IConcept
import org.modelix.model.api.IConceptReference
import org.modelix.model.api.INode
import org.modelix.model.api.INodeReference
import org.modelix.model.api.INodeResolutionScope
import org.modelix.model.api.ITree
import org.modelix.model.api.LocalPNodeReference
import org.modelix.model.api.NodeReference
import org.modelix.model.api.PNodeAdapter
import org.modelix.model.api.PNodeReference
import org.modelix.model.api.getNode

data class PArea(val branch: IBranch) : IArea {

    override fun getRoot(): INode = PNodeAdapter(ITree.ROOT_ID, branch)

    override fun resolveConcept(ref: IConceptReference): IConcept? {
        return null
    }

    override fun resolveNode(ref: INodeReference): INode? {
        return resolveOriginalNode(ref)
    }

    override fun resolveOriginalNode(ref: INodeReference): INode? {
        val pref = when (ref) {
            is NodeReference -> PNodeReference.tryDeserialize(ref.serialized)
            else -> ref
        }
        val nodeId = when (pref) {
            is PNodeReference -> if (pref.branchId == branch.getId()) pref.id else return null
            is LocalPNodeReference -> pref.id
            else -> return null
        }
        return if (containsNode(nodeId)) branch.getNode(nodeId) else null
    }

    override fun resolveBranch(id: String): IBranch? {
        return if (id == branch.getId()) branch else null
    }

    override fun collectAreas(): List<IArea> = listOf(this)

    fun containsNode(nodeId: Long): Boolean = branch.transaction.containsNode(nodeId)

    override fun <T> executeRead(f: () -> T): T = INodeResolutionScope.ensureInContext(this) { branch.computeRead(f) }

    override fun <T> executeWrite(f: () -> T): T = INodeResolutionScope.ensureInContext(this) { branch.computeWrite(f) }

    override fun canRead(): Boolean = branch.canRead()

    override fun canWrite(): Boolean = branch.canWrite()

    override fun addListener(l: IAreaListener) {
        AreaListenerRegistry.registerListener(this, l)
    }

    override fun removeListener(l: IAreaListener) {
        AreaListenerRegistry.unregisterListener(this, l)
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other == null || this::class != other::class) return false

        other as PArea

        if (branch != other.branch) return false

        return true
    }

    override fun hashCode(): Int {
        return branch.hashCode()
    }

    override fun resolveArea(ref: IAreaReference): IArea? {
        return if (ref is AreaReference && ref.branchId == branch.getId()) this else null
    }

    override fun getReference() = AreaReference(branch.getId())

    data class AreaReference(val branchId: String?) : IAreaReference
}

fun IBranch.getArea() = PArea(this)
