package org.modelix.metamodel

import org.modelix.model.api.IConcept
import org.modelix.model.api.INode
import kotlin.reflect.KClass

class ChildListAccessor<ChildT : ITypedNode>(
    parent: INode,
    role: String,
    childConcept: IConcept,
    childType: KClass<ChildT>,
) : ChildAccessor<ChildT>(parent, role, childConcept, childType) {

}