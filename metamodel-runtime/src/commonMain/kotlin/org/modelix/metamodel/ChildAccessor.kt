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
    fun isEmpty(): Boolean = iterator().hasNext()

    override fun iterator(): Iterator<ChildT> {
        return parent.getChildren(role).map {
            val wrapped = when (childConcept) {
                is GeneratedConcept<*, *> -> childConcept.wrap(it)
                else -> throw RuntimeException("Unsupported concept type: ${childConcept::class} (${childConcept.getLongName()})")
            }
            childType.cast(wrapped)
        }.iterator()
    }

    fun addNew(index: Int = -1, concept: IConcept? = null): ChildT {
        return childType.cast(LanguageRegistry.wrapNode(parent.addNewChild(role, index, concept)))
    }

    fun remove(child: INode) {
        parent.removeChild(child)
    }

    fun remove(child: TypedNodeImpl) {
        remove(child._node)
    }
}