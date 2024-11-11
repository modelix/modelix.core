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

import com.badoo.reaktive.observable.take
import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

class FirstElementStep<E>() : MonoTransformingStep<E, E>() {
    override fun canBeMultiple(): Boolean = false

    override fun createStream(input: StepStream<E>, context: IStreamInstantiationContext): StepStream<E> {
        return input.take(1)
    }

    override fun requiresSingularQueryInput(): Boolean = true

    override fun toString(): String {
        return "${getProducer()}\n.first()"
    }

    override fun getOutputSerializer(serializationContext: SerializationContext): KSerializer<out IStepOutput<E>> {
        return getProducer().getOutputSerializer(serializationContext)
    }

    override fun createDescriptor(context: QueryGraphDescriptorBuilder) = FirstElementDescriptor()

    @Serializable
    @SerialName("first")
    class FirstElementDescriptor : CoreStepDescriptor() {
        override fun createStep(context: QueryDeserializationContext): IStep {
            return FirstElementStep<Any?>()
        }

        override fun doNormalize(idReassignments: IdReassignments): StepDescriptor = FirstElementDescriptor()
    }
}

fun <Out> IProducingStep<Out>.first(): IMonoStep<Out> {
    return FirstElementStep<Out>().also { it.connect(this) }
}
