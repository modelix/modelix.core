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

import com.badoo.reaktive.single.Single
import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import org.modelix.streams.exactlyOne
import org.modelix.streams.fold
import org.modelix.streams.getSynchronous

class FoldingStep<In : CommonIn, Out : CommonIn, CommonIn>(private val operation: MonoUnboundQuery<IZip2Output<CommonIn, Out, In>, Out>) : AggregationStep<CommonIn, Out>() {

    private var initialValueProducer: IMonoStep<Out>? = null

    fun getInputProducer(): IProducingStep<In> = getProducer() as IProducingStep<In>
    fun getInitialValueProducer(): IMonoStep<Out> = initialValueProducer!!

    override fun validate() {
        super.validate()
        require(!getInitialValueProducer().canBeMultiple()) { "Not a mono: ${getInitialValueProducer()}" }
    }

    override fun getProducers(): List<IProducingStep<CommonIn>> {
        return super.getProducers() + listOfNotNull<IProducingStep<Out>>(initialValueProducer)
    }

    override fun addProducer(producer: IProducingStep<CommonIn>) {
        if (getProducers().isEmpty()) {
            super.addProducer(producer)
        } else {
            initialValueProducer = producer as IMonoStep<Out>
        }
    }

    override fun aggregate(input: StepStream<CommonIn>, context: IStreamInstantiationContext): Single<IStepOutput<Out>> {
        val initialValue: IStepOutput<Out> = context.getOrCreateStream<Out>(getInitialValueProducer()).exactlyOne().getSynchronous()
        return input.fold(initialValue) { acc, value -> fold(acc, value) }
    }

    private fun fold(acc: IStepOutput<Out>, value: IStepOutput<CommonIn>): IStepOutput<Out> {
        return operation.asStream(QueryEvaluationContext.EMPTY, ZipStepOutput(listOf(acc, value))).exactlyOne().getSynchronous()
    }

    override fun getOutputSerializer(serializationContext: SerializationContext): KSerializer<out IStepOutput<Out>> {
        return operation.getElementOutputSerializer(serializationContext)
    }

    override fun toString(): String {
        return "${getProducer()}\n.fold(${getInitialValueProducer()}) {\n${operation.toString().prependIndent("  ")}\n}"
    }

    override fun createDescriptor(context: QueryGraphDescriptorBuilder) = Descriptor(context.load(operation))

    @Serializable
    @SerialName("fold")
    class Descriptor(val operation: QueryId) : CoreStepDescriptor() {
        override fun createStep(context: QueryDeserializationContext): IStep {
            return FoldingStep(context.getOrCreateQuery(operation) as MonoUnboundQuery<IZip2Output<*, Any?, Any?>, Any?>)
        }

        override fun doNormalize(idReassignments: IdReassignments): StepDescriptor = Descriptor(idReassignments.reassign(operation))

        override fun prepareNormalization(idReassignments: IdReassignments) {
            idReassignments.visitQuery(operation)
        }
    }
}

fun <In> IFluxStep<In>.fold(initial: Int, operation: IStepSharingContext.(acc: IMonoStep<Int>, it: IMonoStep<In>) -> IMonoStep<Int>) = fold(initial.asMono(), operation)

fun <In, Out> IFluxStep<In>.fold(initial: IMonoStep<Out>, operation: IStepSharingContext.(acc: IMonoStep<Out>, it: IMonoStep<In>) -> IMonoStep<Out>): IMonoStep<Out> {
    return FoldingStep<In, Out, Any?>(
        buildMonoQuery<IZip2Output<Any?, Out, In>, Out> { operation(it.first, it.second) }.castToInstance(),
    ).also {
        connect(it)
        initial.connect(it)
    }
}
