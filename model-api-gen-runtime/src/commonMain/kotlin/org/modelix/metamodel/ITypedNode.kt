/*
 * Copyright (c) 2023.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.modelix.metamodel

import org.modelix.model.api.IConcept
import org.modelix.model.api.INode
import org.modelix.model.api.INodeReference
import org.modelix.model.api.IProperty
import org.modelix.model.area.IArea
import kotlin.reflect.KClass

interface ITypedNode {
    val _concept: ITypedConcept
    fun unwrap(): INode
}

fun ITypedNode.untyped() = unwrap()
fun ITypedNode.untypedConcept() = _concept.untyped()
fun ITypedNode.typedConcept() = _concept
fun ITypedNode.getPropertyValue(property: IProperty): String? = unwrap().getPropertyValue(property.name)
fun ITypedNode.instanceOf(concept: ITypedConcept): Boolean {
    return instanceOf(concept._concept)
}
fun ITypedNode.instanceOf(concept: IConcept): Boolean {
    return this._concept._concept.isSubConceptOf(concept)
}

inline fun <reified NodeT : ITypedNode> NodeT.typedReference() = TypedNodeReference(unwrap().reference, NodeT::class)
fun ITypedNode.untypedReference() = unwrap().reference
data class TypedNodeReference<NodeT : ITypedNode>(val ref: INodeReference, val nodeClass: KClass<NodeT>) {
    fun resolve(area: IArea?) = ref.resolveNode(area)?.typed(nodeClass)
    fun untyped() = ref
}
