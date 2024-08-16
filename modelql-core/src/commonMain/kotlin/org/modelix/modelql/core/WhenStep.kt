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
package org.modelix.modelql.core

import com.badoo.reaktive.observable.asObservable
import com.badoo.reaktive.observable.firstOrDefault
import com.badoo.reaktive.observable.flatMap
import com.badoo.reaktive.observable.map
import com.badoo.reaktive.observable.observableOfEmpty
import com.badoo.reaktive.single.flatten
import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import org.modelix.streams.filterBySingle
import kotlin.experimental.ExperimentalTypeInference

class WhenStep<In, Out>(
    val cases: List<Pair<IMonoUnboundQuery<In, Boolean?>, IMonoUnboundQuery<In, Out>>>,
    val elseCase: IMonoUnboundQuery<In, Out>?,
) : MonoTransformingStep<In, Out>() {

    override fun toString(): String {
        return "when()" + cases.joinToString("") { ".if(${it.first}).then(${it.second})" } + ".else($elseCase)"
    }

    override fun canBeEmpty(): Boolean {
        if (elseCase == null) return true
        if (getProducer().canBeEmpty()) return true
        return cases.any { it.second.canBeEmpty() }
    }

    override fun canBeMultiple(): Boolean {
        return false
    }

    override fun createDescriptor(context: QueryGraphDescriptorBuilder): StepDescriptor {
        return Descriptor(
            cases.map { context.load(it.first) to context.load(it.second) },
            elseCase?.let { context.load(it) },
        )
    }

    @Serializable
    @SerialName("when")
    class Descriptor(val cases: List<Pair<QueryId, QueryId>>, val elseCase: QueryId? = null) : StepDescriptor() {
        override fun createStep(context: QueryDeserializationContext): IStep {
            return WhenStep<Any?, Any?>(
                cases.map { context.getOrCreateQuery(it.first) as MonoUnboundQuery<Any?, Boolean?> to context.getOrCreateQuery(it.second) as MonoUnboundQuery<Any?, Any?> },
                elseCase?.let { context.getOrCreateQuery(it) as MonoUnboundQuery<Any?, Any?> },
            )
        }
    }

    override fun getOutputSerializer(serializationContext: SerializationContext): KSerializer<out IStepOutput<Out>> {
        val inputSerializer = getProducer().getOutputSerializer(serializationContext).upcast()
        return MultiplexedOutputSerializer<Out>(
            this,
            cases.map {
                it.second.getElementOutputSerializer(serializationContext + (it.second.castToInstance().inputStep to inputSerializer)).upcast()
            } +
                listOfNotNull(
                    elseCase?.let {
                        it.getElementOutputSerializer(serializationContext + (it.castToInstance().inputStep to inputSerializer))
                    }?.upcast(),
                ),
        )
    }

    override fun createFlow(input: StepFlow<In>, context: IFlowInstantiationContext): StepFlow<Out> {
        return input.flatMap { inputElement ->
            cases.withIndex().asObservable().filterBySingle { (index, case) ->
                case.first.asFlow(context.evaluationContext, inputElement).map { it.value == true }.firstOrDefault(false)
            }.map { (index, case) ->
                case.second.asFlow(context.evaluationContext, inputElement).map { MultiplexedOutput(index, it) }
            }.firstOrDefault {
                val elseCaseIndex = cases.size
                elseCase?.asFlow(context.evaluationContext, inputElement)?.map { MultiplexedOutput(elseCaseIndex, it) }
                    ?: observableOfEmpty()
            }.flatten()
        }
    }
}

@OptIn(ExperimentalTypeInference::class)
class WhenStepBuilder<In, Out>(private val input: IMonoStep<In>) {
    private val cases = ArrayList<Pair<IMonoUnboundQuery<In, Boolean?>, IMonoUnboundQuery<In, Out>>>()

    fun `if`(condition: (IMonoStep<In>) -> IMonoStep<Boolean?>): CaseBuilder {
        return CaseBuilder(buildMonoQuery { condition(it) })
    }

    @BuilderInference
    fun `else`(body: (IMonoStep<In>) -> IMonoStep<Out>): IMonoStep<Out> {
        return WhenStep(cases, buildMonoQuery { body(it) }).connectAndDowncast(input)
    }

    inner class CaseBuilder(val condition: IMonoUnboundQuery<In, Boolean?>) {
        @BuilderInference
        fun then(body: (IMonoStep<In>) -> IMonoStep<Out>): WhenStepBuilder<In, Out> {
            cases += condition to buildMonoQuery { body(it) }
            return this@WhenStepBuilder
        }
    }
}

fun <In, Out> IMonoStep<In>.`when`() = WhenStepBuilder<In, Out>(this)
