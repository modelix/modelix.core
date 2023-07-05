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
package org.modelix.modelql.typed

import org.modelix.metamodel.IConceptOfTypedNode
import org.modelix.metamodel.ITypedConcept
import org.modelix.metamodel.ITypedNode
import org.modelix.modelql.core.IMonoStep
import org.modelix.modelql.core.IMonoUnboundQuery
import org.modelix.modelql.core.IUnboundQuery
import org.modelix.modelql.core.WhenStep
import kotlin.experimental.ExperimentalTypeInference

@OptIn(ExperimentalTypeInference::class)
class ConceptSwitchBuilder<In : ITypedNode, Out>() {
    private val cases = ArrayList<Pair<IMonoUnboundQuery<In, Boolean>, IMonoUnboundQuery<In, Out>>>()

    fun <TNode : In, TConcept : IConceptOfTypedNode<TNode>> `if`(concept: TConcept): CaseBuilder<TNode> {
        return CaseBuilder<TNode>(concept)
    }

    @BuilderInference
    fun `else`(body: (IMonoStep<In>) -> IMonoStep<Out>): IMonoStep<Out> {
        return WhenStep(cases, IUnboundQuery.buildMono(body))
    }

    inner class CaseBuilder<Node : In>(val concept: IConceptOfTypedNode<Node>) {
        @BuilderInference
        fun then(body: (IMonoStep<Node>) -> IMonoStep<Out>): ConceptSwitchBuilder<In, Out> {
            cases += IUnboundQuery.buildMono<ITypedNode, Boolean> { it.instanceOf(concept) } to (IUnboundQuery.buildMono(body) as IMonoUnboundQuery<In, Out>)
            return this@ConceptSwitchBuilder
        }
    }
}

fun <In :ITypedNode, Out> IMonoStep<In>.conceptSwitch() = ConceptSwitchBuilder<In, Out>()