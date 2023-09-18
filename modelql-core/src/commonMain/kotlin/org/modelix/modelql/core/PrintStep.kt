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

import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.modules.SerializersModule

class PrintStep<E>(val prefix: String) : MonoTransformingStep<E, E>() {
    override fun getOutputSerializer(serializersModule: SerializersModule): KSerializer<out IStepOutput<E>> {
        return getProducer().getOutputSerializer(serializersModule)
    }

    override fun transform(evaluationContext: QueryEvaluationContext, input: E): E {
        println(prefix + input)
        return input
    }

    override fun createDescriptor(context: QueryGraphDescriptorBuilder): StepDescriptor = Descriptor(prefix)

    @Serializable
    @SerialName("print")
    class Descriptor(val prefix: String = "") : CoreStepDescriptor() {
        override fun createStep(context: QueryDeserializationContext): IStep {
            return PrintStep<Any?>(prefix)
        }
    }

    override fun toString(): String {
        return "${getProducers().single()}.print(\"$prefix\")"
    }
}

fun <T> IMonoStep<T>.print(prefix: String = "") = PrintStep<T>(prefix).connectAndDowncast(this)
fun <T> IFluxStep<T>.print(prefix: String = "") = PrintStep<T>(prefix).connectAndDowncast(this)
