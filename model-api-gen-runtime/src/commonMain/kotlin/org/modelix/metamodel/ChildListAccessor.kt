package org.modelix.metamodel

import org.modelix.model.api.IChildLink
import org.modelix.model.api.IConcept
import org.modelix.model.api.INode
import kotlin.js.JsExport
import kotlin.reflect.KClass

@JsExport
class ChildListAccessor<ChildT : ITypedNode>(
    parent: INode,
    role: IChildLink,
    childConcept: IConcept,
    childType: KClass<ChildT>,
) : ChildAccessor<ChildT>(parent, role, childConcept, childType)
