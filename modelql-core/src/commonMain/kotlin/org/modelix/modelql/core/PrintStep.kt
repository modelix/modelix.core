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

import com.badoo.reaktive.observable.map
import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

class PrintStep<E>(val prefix: String) : MonoTransformingStep<E, E>() {
    override fun getOutputSerializer(serializationContext: SerializationContext): KSerializer<out IStepOutput<E>> {
        return getProducer().getOutputSerializer(serializationContext)
    }

    override fun createStream(input: StepStream<E>, context: IStreamInstantiationContext): StepStream<E> {
        return input.map {
            println(prefix + it.value)
            it
        }
    }

    override fun createDescriptor(context: QueryGraphDescriptorBuilder): StepDescriptor = Descriptor(prefix)

    @Serializable
    @SerialName("print")
    data class Descriptor(val prefix: String = "") : CoreStepDescriptor() {
        override fun createStep(context: QueryDeserializationContext): IStep {
            return PrintStep<Any?>(prefix)
        }

        override fun doNormalize(idReassignments: IdReassignments): StepDescriptor = Descriptor(prefix)
    }

    override fun toString(): String {
        return "${getProducers().single()}\n.print(\"$prefix\")"
    }
}

fun <T> IMonoStep<T>.print(prefix: String = "") = PrintStep<T>(prefix).connectAndDowncast(this)
fun <T> IFluxStep<T>.print(prefix: String = "") = PrintStep<T>(prefix).connectAndDowncast(this)
