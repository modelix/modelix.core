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

import com.badoo.reaktive.maybe.defaultIfEmpty
import com.badoo.reaktive.observable.defaultIfEmpty
import com.badoo.reaktive.observable.filter
import com.badoo.reaktive.observable.firstOrDefault
import com.badoo.reaktive.observable.map
import com.badoo.reaktive.observable.repeat
import com.badoo.reaktive.observable.zipWith
import com.badoo.reaktive.single.notNull
import kotlinx.coroutines.flow.filter
import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import org.modelix.streams.exactlyOne
import org.modelix.streams.filterBySingle
import org.modelix.streams.firstOrNull

class FilteringStep<E>(val condition: MonoUnboundQuery<E, Boolean?>) : TransformingStep<E, E>(), IMonoStep<E>, IFluxStep<E> {

    override fun canBeEmpty(): Boolean = true

    override fun canBeMultiple(): Boolean = getProducer().canBeMultiple()

    override fun validate() {
        super<TransformingStep>.validate()
        require(!condition.requiresWriteAccess()) { "write access not allowed inside a filtering step: $this" }
        require(!condition.outputStep.canBeMultiple()) {
            "filter condition should not return multiple elements: $condition"
        }
    }

    override fun createFlow(input: StepFlow<E>, context: IFlowInstantiationContext): StepFlow<E> {
        return input.filterBySingle { condition.asFlow(context.evaluationContext, it).map { it.value == true }.firstOrDefault(false) }
    }

    override fun getOutputSerializer(serializationContext: SerializationContext): KSerializer<out IStepOutput<E>> {
        return getProducer().getOutputSerializer(serializationContext)
    }

    override fun toString(): String {
        return """${getProducers().single()}.filter { $condition }"""
    }

    override fun createDescriptor(context: QueryGraphDescriptorBuilder) = Descriptor(context.load(condition))

    @Serializable
    @SerialName("filter")
    class Descriptor(val queryId: QueryId) : CoreStepDescriptor() {
        override fun createStep(context: QueryDeserializationContext): IStep {
            return FilteringStep<Any?>(context.getOrCreateQuery(queryId) as MonoUnboundQuery<Any?, Boolean?>)
        }
    }
}

fun <T> IFluxStep<T>.filter(condition: IQueryBuilderContext<T, Boolean>.(IMonoStep<T>) -> IMonoStep<Boolean>): IFluxStep<T> {
    return FilteringStep(buildMonoQuery(condition).castToInstance()).also { connect(it) }
}
fun <T> IMonoStep<T>.filter(condition: IQueryBuilderContext<T, Boolean>.(IMonoStep<T>) -> IMonoStep<Boolean>): IMonoStep<T> {
    return FilteringStep(buildMonoQuery(condition).castToInstance()).also { connect(it) }
}
