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
package org.modelix.modelql.core

import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEmpty
import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.modules.SerializersModule
import kotlin.jvm.JvmName

class IfEmptyStep<In : Out, Out>(val alternative: UnboundQuery<Unit, *, Out>) : TransformingStep<In, Out>(), IFluxOrMonoStep<Out> {
    override fun createFlow(input: StepFlow<In>, context: IFlowInstantiationContext): StepFlow<Out> {
        val downCastedInput: StepFlow<Out> = input
        return downCastedInput.map { MultiplexedOutput(0, it) }.onEmpty {
            emitAll(alternative.asFlow(context.evaluationContext, Unit).map { MultiplexedOutput(1, it) })
        }
    }

    override fun createSequence(evaluationContext: QueryEvaluationContext, queryInput: Sequence<Any?>): Sequence<Out> {
        return getProducer().createSequence(evaluationContext, queryInput)
            .ifEmpty { alternative.outputStep.createSequence(evaluationContext, sequenceOf(Unit)) }
    }

    override fun canBeEmpty(): Boolean = alternative.outputStep.canBeEmpty()

    override fun canBeMultiple(): Boolean = getProducer().canBeMultiple() || alternative.outputStep.canBeMultiple()
    override fun requiresSingularQueryInput(): Boolean = true

    override fun getOutputSerializer(serializersModule: SerializersModule): KSerializer<out IStepOutput<Out>> {
        return MultiplexedOutputSerializer(
            this,
            listOf(
                getProducer().getOutputSerializer(serializersModule).upcast(),
                alternative.getElementOutputSerializer(serializersModule).upcast(),
            ),
        )
    }

    override fun createDescriptor(context: QueryGraphDescriptorBuilder) = Descriptor(context.load(alternative))

    @Serializable
    @SerialName("ifEmpty")
    class Descriptor(val alternative: QueryId) : CoreStepDescriptor() {
        override fun createStep(context: QueryDeserializationContext): IStep {
            return IfEmptyStep<Any?, Any?>(context.getOrCreateQuery(alternative) as UnboundQuery<Unit, *, Any?>)
        }
    }

    override fun toString(): String {
        return """${getProducers().single()}.ifEmpty { $alternative }"""
    }
}

@JvmName("ifEmpty_mono_mono")
fun <In : Out, Out> IMonoStep<In>.ifEmpty(alternative: () -> IMonoStep<Out>): IMonoStep<Out> {
    return IfEmptyStep<In, Out>(buildMonoQuery<Unit, Out> { alternative() }.castToInstance()).also { connect(it) }
}

@JvmName("ifEmpty_flux_mono")
fun <In : Out, Out> IFluxStep<In>.ifEmpty(alternative: () -> IMonoStep<Out>): IFluxStep<Out> {
    return IfEmptyStep<In, Out>(buildMonoQuery<Unit, Out> { alternative() }.castToInstance()).also { connect(it) }
}

@JvmName("ifEmpty_mono_flux")
fun <In : Out, Out> IMonoStep<In>.ifEmptyFlux(alternative: () -> IFluxStep<Out>): IFluxStep<Out> {
    return IfEmptyStep<In, Out>(buildFluxQuery<Unit, Out> { alternative() }.castToInstance()).also { connect(it) }
}

@JvmName("ifEmpty_flux_flux")
fun <In : Out, Out> IFluxStep<In>.ifEmptyFlux(alternative: () -> IFluxStep<Out>): IFluxStep<Out> {
    return IfEmptyStep<In, Out>(buildFluxQuery<Unit, Out> { alternative() }.castToInstance()).also { connect(it) }
}
