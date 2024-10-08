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

import com.badoo.reaktive.observable.skip
import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

class DropStep<E>(val count: Int) : TransformingStep<E, E>(), IMonoStep<E>, IFluxStep<E> {

    override fun canBeEmpty(): Boolean = true

    override fun canBeMultiple(): Boolean = getProducer().canBeMultiple()

    override fun createFlow(input: StepFlow<E>, context: IFlowInstantiationContext): StepFlow<E> {
        return input.skip(count.toLong())
    }

    override fun getOutputSerializer(serializationContext: SerializationContext): KSerializer<out IStepOutput<E>> {
        return getProducer().getOutputSerializer(serializationContext)
    }

    override fun toString(): String {
        return "${getProducers().single()}\n.drop($count)"
    }

    override fun createDescriptor(context: QueryGraphDescriptorBuilder) = Descriptor(count)

    @Serializable
    @SerialName("drop")
    data class Descriptor(val count: Int) : CoreStepDescriptor() {
        override fun createStep(context: QueryDeserializationContext): IStep {
            return DropStep<Any?>(count)
        }

        override fun doNormalize(idReassignments: IdReassignments): StepDescriptor = Descriptor(count)
    }
}

fun <T> IFluxStep<T>.drop(count: Int): IFluxStep<T> {
    return DropStep<T>(count).also { connect(it) }
}
