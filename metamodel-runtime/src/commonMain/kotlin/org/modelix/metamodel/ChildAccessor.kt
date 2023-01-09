package org.modelix.metamodel

import org.modelix.model.api.IConcept
import org.modelix.model.api.INode
import kotlin.reflect.KClass
import kotlin.reflect.cast

abstract class ChildAccessor<ChildT : ITypedNode>(
    protected val parent: INode,
    protected val role: String,
    protected val childConcept: IConcept,
    protected val childType: KClass<ChildT>,
): Iterable<ChildT> {
    fun isEmpty(): Boolean = !iterator().hasNext()

    fun getSize(): Int {
        return this.count()
    }

    override fun iterator(): Iterator<ChildT> {
        return parent.getChildren(role).map {
            when (childConcept) {
                is GeneratedConcept<*, *> -> it.typed(childType)
                else -> throw RuntimeException("Unsupported concept type: ${childConcept::class} (${childConcept.getLongName()})")
            }
        }.iterator()
    }

    fun addNew(index: Int = -1, concept: IConcept? = null): ChildT {
        return childType.cast(parent.addNewChild(role, index, concept).typed())
    }

    fun removeUnwrapped(child: INode) {
        parent.removeChild(child)
    }

    fun remove(child: TypedNodeImpl) {
        removeUnwrapped(child.unwrap())
    }
}
