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

import org.modelix.model.api.INode
import org.modelix.model.api.IReferenceLink

interface ITypedReferenceLink<TargetT : ITypedNode> : ITypedConceptFeature {
    fun untyped(): IReferenceLink
    fun castTarget(target: INode): TargetT
    fun getTypedTargetConcept(): IConceptOfTypedNode<TargetT>
}
fun <TargetT : ITypedNode> INode.getReferenceTargetOrNull(link: ITypedReferenceLink<TargetT>): TargetT? {
    return getReferenceTarget(link.untyped())?.let { link.castTarget(it) }
}
fun <TargetT : ITypedNode> INode.getReferenceTarget(link: ITypedReferenceLink<TargetT>): TargetT {
    val target = getReferenceTargetOrNull(link)
    if (target == null && !link.untyped().isOptional) throw ReferenceNotSetException(this, link)
    return target as TargetT
}
fun <TargetT : ITypedNode> INode.setReferenceTarget(link: ITypedReferenceLink<TargetT>, target: TargetT?) {
    setReferenceTarget(link.untyped(), target?.unwrap())
}

class ReferenceNotSetException(val node: INode, val link: ITypedReferenceLink<*>) :
    Exception("Node $node has no reference target in role ${link.untyped().name}")
