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

import kotlinx.coroutines.flow.take
import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.modules.SerializersModule

class FirstElementStep<E>() : MonoTransformingStep<E, E>() {
    override fun canBeMultiple(): Boolean = false

    override fun createFlow(input: StepFlow<E>, context: IFlowInstantiationContext): StepFlow<E> {
        return input.take(1)
    }

    override fun requiresSingularQueryInput(): Boolean = true

    override fun transform(evaluationContext: QueryEvaluationContext, input: IStepOutput<E>): IStepOutput<E> {
        return input
    }

    override fun transform(evaluationContext: QueryEvaluationContext, input: E): E {
        return input
    }

    override fun toString(): String {
        return getProducer().toString() + ".first()"
    }

    override fun getOutputSerializer(serializersModule: SerializersModule): KSerializer<out IStepOutput<E>> {
        return getProducer().getOutputSerializer(serializersModule)
    }

    override fun createDescriptor(context: QueryGraphDescriptorBuilder) = FirstElementDescriptor()

    @Serializable
    @SerialName("first")
    class FirstElementDescriptor : CoreStepDescriptor() {
        override fun createStep(context: QueryDeserializationContext): IStep {
            return FirstElementStep<Any?>()
        }
    }
}

fun <Out> IProducingStep<Out>.first(): IMonoStep<Out> {
    return FirstElementStep<Out>().also { it.connect(this) }
}
