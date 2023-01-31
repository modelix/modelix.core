/*
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
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
import org.modelix.model.api.getReferenceTarget
import org.modelix.model.api.setReferenceTarget

interface ITypedReferenceLink<TargetT : ITypedNode?> : ITypedConceptFeature {
    fun untyped(): IReferenceLink
    fun castTarget(target: INode?): TargetT
}
interface ITypedMandatoryReferenceLink<TargetT : ITypedNode> : ITypedReferenceLink<TargetT>
fun <TargetT : ITypedNode?> INode.getReferenceTarget(link: ITypedReferenceLink<TargetT>): TargetT {
    return link.castTarget(getReferenceTarget(link.untyped()))
}
fun <TargetT : ITypedNode?> INode.setReferenceTarget(link: ITypedReferenceLink<TargetT>, target: TargetT) {
    setReferenceTarget(link.untyped(), target?.unwrap())
}