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
import org.modelix.metamodel.ITypedNode
import org.modelix.model.api.IConcept
import org.modelix.modelql.core.IBoundFragment
import org.modelix.modelql.core.IMonoStep
import org.modelix.modelql.core.IMonoUnboundQuery
import org.modelix.modelql.core.IRecursiveFragmentBuilder
import org.modelix.modelql.core.IUnboundQuery
import org.modelix.modelql.core.WhenStep
import org.modelix.modelql.core.bindFragment
import org.modelix.modelql.core.connect
import kotlin.experimental.ExperimentalTypeInference

@OptIn(ExperimentalTypeInference::class)
class ConceptSwitchBuilder<In : ITypedNode, Out>(private val input: IMonoStep<In>) {
    private val cases = HashMap<IConcept, IMonoUnboundQuery<In, Out>>()

    @Suppress("FunctionName")
    fun <TNode : In, TConcept : IConceptOfTypedNode<TNode>> `if`(concept: TConcept): CaseBuilder<TNode> {
        return CaseBuilder<TNode>(concept)
    }

    @BuilderInference
    @Suppress("FunctionName")
    fun `else`(elseBody: (IMonoStep<In>) -> IMonoStep<Out>): IMonoStep<Out> {
        val visited = HashSet<IConcept>()
        val sortedCases = LinkedHashMap<IConcept, IMonoUnboundQuery<In, Out>>()
        fun collectCases(c: IConcept) {
            if (visited.contains(c)) return
            visited += c
            for (directSuperConcept in c.getDirectSuperConcepts()) {
                collectCases(directSuperConcept)
            }
            val caseThen = cases[c] ?: return
            sortedCases[c] = caseThen
        }
        cases.forEach { collectCases(it.key) }
        if (cases.size != sortedCases.size) throw RuntimeException("Sort algorithm deleted some concept switch cases")

        return WhenStep(
            sortedCases.toList().asReversed()
                .map { case -> IUnboundQuery.buildMono { it.untyped().instanceOf(case.first) } to case.second },
            IUnboundQuery.buildMono(elseBody),
        ).also { input.connect(it) }
    }

    inner class CaseBuilder<Node : In>(val concept: IConceptOfTypedNode<Node>) {
        @BuilderInference
        fun then(body: (IMonoStep<Node>) -> IMonoStep<Out>): ConceptSwitchBuilder<In, Out> {
            cases += concept.untyped() to (IUnboundQuery.buildMono(body) as IMonoUnboundQuery<In, Out>)
            return this@ConceptSwitchBuilder
        }
    }
}

fun <In : ITypedNode, Out> IMonoStep<In>.conceptSwitch() = ConceptSwitchBuilder<In, Out>(this)

fun <In : ITypedNode, Context> IMonoStep<In>.conceptSwitchFragment() = ConceptSwitchBuilder<In, IBoundFragment<Context>>(this)
fun <In : ITypedNode, CaseIn : In, Context> ConceptSwitchBuilder<In, IBoundFragment<Context>>.CaseBuilder<CaseIn>.thenFragment(body: IRecursiveFragmentBuilder<CaseIn, Context>.() -> Unit): ConceptSwitchBuilder<In, IBoundFragment<Context>> {
    return then {
        it.bindFragment<CaseIn, Context> {
            body()
        }
    }
}
fun <In : ITypedNode, Context> ConceptSwitchBuilder<In, IBoundFragment<Context>>.elseFragment(body: IRecursiveFragmentBuilder<In, Context>.() -> Unit): IMonoStep<IBoundFragment<Context>> {
    return `else` {
        it.bindFragment<In, Context> {
            body()
        }
    }
}
