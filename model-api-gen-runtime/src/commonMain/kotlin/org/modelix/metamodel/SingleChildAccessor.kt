package org.modelix.metamodel

import org.modelix.model.api.IChildLink
import org.modelix.model.api.IConcept
import org.modelix.model.api.INode
import kotlin.reflect.KClass

class SingleChildAccessor<ChildT : ITypedNode>(
    parent: INode,
    role: IChildLink,
    childConcept: IConcept,
    childType: KClass<ChildT>,
) : ChildAccessor<ChildT>(parent, role, childConcept, childType) {
    fun isSet(): Boolean = !isEmpty()
    fun get(): ChildT? = iterator().let { if (it.hasNext()) it.next() else null }
    fun <T> read(receiver: (ChildT?) -> T): T = receiver(get())
    fun setNew(): ChildT {
        get()?.let { parent.removeChild(it.unwrap()) }
        return addNew()
    }
    fun <NewChildT : ChildT> setNew(concept: INonAbstractConcept<NewChildT>): NewChildT {
        require(concept.untyped().isSubConceptOf(childConcept)) {
            "$concept is not a sub concept of $childConcept"
        }
        get()?.let { parent.removeChild(it.unwrap()) }
        return addNew(concept = concept)
    }
}

fun <NewChildT : ChildT, ChildT : ITypedNode> SingleChildAccessor<ChildT>.setNew(concept: INonAbstractConcept<NewChildT>, initializer: NewChildT.() -> Unit): NewChildT {
    return setNew(concept).apply(initializer)
}
fun <ChildT : ITypedNode> SingleChildAccessor<ChildT>.setNew(initializer: ChildT.() -> Unit): ChildT {
    return setNew().apply(initializer)
}
