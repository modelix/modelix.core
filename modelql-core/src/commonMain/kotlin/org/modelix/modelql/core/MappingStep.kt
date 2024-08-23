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

import com.badoo.reaktive.observable.flatMap
import com.badoo.reaktive.observable.observableOf
import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

class MappingStep<In, Out>(val query: MonoUnboundQuery<In, Out>) : MonoTransformingStep<In, Out>() {

    override fun validate() {
        super.validate()
        check(query.outputStep != this)
    }

    override fun canBeEmpty(): Boolean = getProducer().canBeEmpty() || query.outputStep.canBeEmpty()

    override fun canBeMultiple(): Boolean = getProducer().canBeMultiple() || query.outputStep.canBeMultiple()

    override fun requiresSingularQueryInput(): Boolean {
        return super.requiresSingularQueryInput() || query.inputStep.requiresSingularQueryInput()
    }

    override fun requiresWriteAccess(): Boolean {
        return query.requiresWriteAccess()
    }

    override fun createFlow(input: StepFlow<In>, context: IFlowInstantiationContext): StepFlow<Out> {
        return input.flatMap { query.asFlow(context.evaluationContext, observableOf(it)) }
    }

    override fun getOutputSerializer(serializationContext: SerializationContext): KSerializer<out IStepOutput<Out>> {
        return query.getAggregationOutputSerializer(serializationContext + (query.inputStep to getProducer().getOutputSerializer(serializationContext)))
    }

    override fun toString(): String {
        return "${getProducer()}.map { $query }"
    }

    override fun createDescriptor(context: QueryGraphDescriptorBuilder) = Descriptor(context.load(query))

    @Serializable
    @SerialName("map")
    class Descriptor(val queryId: QueryId) : CoreStepDescriptor() {
        override fun createStep(context: QueryDeserializationContext): IStep {
            return MappingStep<Any?, Any?>(context.getOrCreateQuery(queryId) as MonoUnboundQuery<Any?, Any?>)
        }
    }
}

fun <In, Out> IFluxStep<In>.map(body: IStepSharingContext.(IMonoStep<In>) -> IMonoStep<Out>): IFluxStep<Out> {
    return MappingStep(buildMonoQuery { body(it) }.castToInstance()).connectAndDowncast(this)
}
fun <In, Out> IMonoStep<In>.map(body: IStepSharingContext.(IMonoStep<In>) -> IMonoStep<Out>): IMonoStep<Out> {
    return MappingStep(buildMonoQuery { body(it) }.castToInstance()).connectAndDowncast(this)
}
fun <In, Out> IMonoStep<In>.map(query: IMonoUnboundQuery<In, Out>): IMonoStep<Out> {
    return MappingStep(query.castToInstance()).connectAndDowncast(this)
}
fun <In, Out> IFluxStep<In>.map(query: IMonoUnboundQuery<In, Out>): IFluxStep<Out> {
    return MappingStep(query.castToInstance()).connectAndDowncast(this)
}
