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

import kotlinx.coroutines.flow.asFlow
import kotlinx.coroutines.flow.flattenConcat
import kotlinx.coroutines.flow.map
import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.modules.SerializersModule

class JoinStep<E>() : ProducingStep<E>(), IConsumingStep<E>, IFluxStep<E> {
    override fun canBeEmpty(): Boolean = getProducers().all { it.canBeEmpty() }
    override fun canBeMultiple(): Boolean = true
    override fun requiresSingularQueryInput(): Boolean = true

    private val producers = ArrayList<IProducingStep<E>>()

    override fun getProducers(): List<IProducingStep<E>> {
        return producers
    }

    override fun createDescriptor(context: QueryGraphDescriptorBuilder) = Descriptor()

    @Serializable
    @SerialName("join")
    class Descriptor : CoreStepDescriptor() {
        override fun createStep(context: QueryDeserializationContext): IStep {
            return JoinStep<Any?>()
        }
    }

    override fun addProducer(producer: IProducingStep<E>) {
        if (getProducers().contains(producer)) return
        producers.add(producer)
        producer.addConsumer(this)
    }

    override fun createFlow(context: IFlowInstantiationContext): StepFlow<E> {
        return producers.mapIndexed { prodIndex, it -> context.getOrCreateFlow(it).map { MultiplexedOutput(prodIndex, it) } }
            .asFlow().flattenConcat()
    }

    override fun getOutputSerializer(serializersModule: SerializersModule): KSerializer<out IStepOutput<E>> {
        return MultiplexedOutputSerializer(this, getProducers().map { it.getOutputSerializer(serializersModule).upcast() })
    }

    override fun toString(): String {
        return getProducers().joinToString(" + ")
    }
}

operator fun <Common> IProducingStep<Common>.plus(other: IProducingStep<Common>): IFluxStep<Common> = JoinStep<Common>().also {
    it.connect(this)
    it.connect(other)
}
