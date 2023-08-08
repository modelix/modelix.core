package org.modelix.metamodel

import org.modelix.model.api.IChildLink
import org.modelix.model.api.IConcept
import org.modelix.model.api.INode
import kotlin.reflect.KClass

abstract class ChildAccessor<ChildT : ITypedNode>(
    protected val parent: INode,
    protected val role: IChildLink,
    protected val childConcept: IConcept,
    val childType: KClass<ChildT>,
) : Iterable<ChildT> {
    fun isEmpty(): Boolean = !iterator().hasNext()

    fun getSize(): Int {
        return this.count()
    }

    fun untypedNodes(): Iterable<INode> = parent.getChildren(role)

    override fun iterator(): Iterator<ChildT> {
        return untypedNodes().map {
            when (childConcept) {
                is GeneratedConcept<*, *> -> it.typed(childType)
                else -> throw RuntimeException("Unsupported concept type: ${childConcept::class} (${childConcept.getLongName()})")
            }
        }.iterator()
    }

    fun addNew(index: Int = -1): ChildT {
        return parent.addNewChild(role, index, childConcept).typed(childType)
    }

    fun <NewNodeT : ChildT> addNew(index: Int = -1, concept: INonAbstractConcept<NewNodeT>): NewNodeT {
        return parent.addNewChild(role, index, concept.untyped()).typed(concept.getInstanceClass())
    }

    fun <NewNodeT : ChildT> addNew(concept: INonAbstractConcept<NewNodeT>): NewNodeT {
        return addNew(-1, concept)
    }

    fun removeUnwrapped(child: INode) {
        parent.removeChild(child)
    }

    fun remove(child: TypedNodeImpl) {
        removeUnwrapped(child.unwrap())
    }
}

fun <ChildT : ITypedNode> ChildAccessor<ChildT>.filterLoaded(): List<ChildT> {
    return untypedNodes().asSequence().filter { it.isValid }.map { it.typed(childType) }.toList()
}
