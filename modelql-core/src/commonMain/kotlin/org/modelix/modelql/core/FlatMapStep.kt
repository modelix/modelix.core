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

import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

class FlatMapStep<In, Out>(val query: FluxUnboundQuery<In, Out>) : TransformingStep<In, Out>(), IFluxStep<Out> {

    override fun canBeEmpty(): Boolean = getProducer().canBeEmpty() || query.outputStep.canBeEmpty()

    override fun canBeMultiple(): Boolean = getProducer().canBeMultiple() || query.outputStep.canBeMultiple()

    override fun requiresWriteAccess(): Boolean {
        return query.requiresWriteAccess()
    }

    override fun createFlow(input: StepFlow<In>, context: IFlowInstantiationContext): StepFlow<Out> {
        return input.flatMapConcat { query.asFlow(context, it) }
    }

    override fun getOutputSerializer(serializationContext: SerializationContext): KSerializer<out IStepOutput<Out>> {
        return query.outputStep.getOutputSerializer(serializationContext + (query.inputStep to getProducer().getOutputSerializer(serializationContext)))
    }

    override fun createDescriptor(context: QueryGraphDescriptorBuilder) = Descriptor(context.load(query))

    @Serializable
    @SerialName("flatMap")
    class Descriptor(val queryId: QueryId) : CoreStepDescriptor() {
        override fun createStep(context: QueryDeserializationContext): IStep {
            return FlatMapStep<Any?, Any?>(context.getOrCreateQuery(queryId) as FluxUnboundQuery<Any?, Any?>)
        }
    }

    override fun toString(): String {
        return """${getProducers().single()}.flatMap { $query }"""
    }
}

fun <In, Out> IProducingStep<In>.flatMap(body: (IMonoStep<In>) -> IFluxStep<Out>): IFluxStep<Out> {
    return flatMap(buildFluxQuery { body(it) })
}
fun <In, Out> IProducingStep<In>.flatMap(query: IFluxUnboundQuery<In, Out>): IFluxStep<Out> {
    return FlatMapStep(query.castToInstance()).also { connect(it) }
}
