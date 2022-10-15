package org.modelix.metamodel

import org.modelix.model.api.IConcept
import org.modelix.model.api.INode
import kotlin.js.JsExport
import kotlin.reflect.KClass
import kotlin.reflect.cast

@JsExport
abstract class ChildAccessor<ChildT : ITypedNode>(
    protected val parent: INode,
    protected val role: String,
    protected val childConcept: IConcept,
    protected val childType: KClass<ChildT>,
): Iterable<ChildT> {
    fun isEmpty(): Boolean = iterator().hasNext()

    fun getSize(): Int {
        return this.count()
    }

    override fun iterator(): Iterator<ChildT> {
        return parent.getChildren(role).map {
            val wrapped = when (childConcept) {
                is GeneratedConcept<*, *> -> it.typed()
                else -> throw RuntimeException("Unsupported concept type: ${childConcept::class} (${childConcept.getLongName()})")
            }
            childType.cast(wrapped)
        }.iterator()
    }

    fun asArray(): Array<out ChildT> {
        return iterableToArray(childType, this)
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

expect fun <T : Any> iterableToArray(elementsType: KClass<T>, elements: Iterable<T>): Array<out T>