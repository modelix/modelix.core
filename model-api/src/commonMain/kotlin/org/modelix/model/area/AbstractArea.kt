package org.modelix.model.area

import org.modelix.model.api.IBranch
import org.modelix.model.api.INode
import org.modelix.model.api.INodeReference

abstract class AbstractArea : IArea {

    override fun resolveNode(ref: INodeReference): INode? = resolveOriginalNode(ref)

    override fun resolveBranch(id: String): IBranch? = null

    override fun collectAreas(): List<IArea> = listOf(this)

    override fun <T> executeRead(f: () -> T): T = f()

    override fun <T> executeWrite(f: () -> T): T = f()

    override fun canRead(): Boolean = true

    override fun canWrite(): Boolean = true

    override fun addListener(l: IAreaListener) {
        throw UnsupportedOperationException()
    }

    override fun removeListener(l: IAreaListener) {
        throw UnsupportedOperationException()
    }

    override fun resolveArea(ref: IAreaReference): IArea? {
        return if (getReference() == ref) this else null
    }
}
