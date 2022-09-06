package org.modelix.metamodel

import org.modelix.model.api.IConcept
import org.modelix.model.api.INode
import kotlin.reflect.KClass

class SingleChildAccessor<ChildT : ITypedNode>(
    parent: INode,
    role: String,
    childConcept: IConcept,
    childType: KClass<ChildT>,
) : ChildAccessor<ChildT>(parent, role, childConcept, childType) {
    fun isSet(): Boolean = !isEmpty()
    fun get(): ChildT? = iterator().let { if (it.hasNext()) it.next() else null }
    fun <T> get(receiver: (ChildT?)->T): T = receiver(get())
    fun setNew(concept: IConcept? = null): ChildT {
        require(concept == null || concept.isSubConceptOf(childConcept)) {
            "$concept is not a sub concept of $childConcept"
        }
        get()?.let { parent.removeChild(it._node) }
        return addNew(concept = concept)
    }
}