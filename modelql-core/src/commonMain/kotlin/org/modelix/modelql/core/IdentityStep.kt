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

open class IdentityStep<E> : TransformingStep<E, E>(), IFluxOrMonoStep<E> {
    override fun getOutputSerializer(serializersModule: SerializersModule): KSerializer<out IStepOutput<E>> {
        return getProducer().getOutputSerializer(serializersModule)
    }

    override fun createFlow(input: StepFlow<E>, context: IFlowInstantiationContext): StepFlow<E> {
        return input
    }

    override fun createSequence(evaluationContext: QueryEvaluationContext, queryInput: Sequence<Any?>): Sequence<E> {
        return getProducer().createSequence(evaluationContext, queryInput)
    }

    override fun canBeEmpty(): Boolean = getProducer().canBeEmpty()

    override fun canBeMultiple(): Boolean = getProducer().canBeMultiple()

    override fun createDescriptor(context: QueryGraphDescriptorBuilder): StepDescriptor {
        return IdentityStepDescriptor()
    }

    override fun toString(): String {
        return "${getProducer()}.identity()"
    }

    @Serializable
    @SerialName("identity")
    class IdentityStepDescriptor : CoreStepDescriptor() {
        override fun createStep(context: QueryDeserializationContext): IStep {
            return IdentityStep<Any?>()
        }
    }
}

fun <T> IMonoStep<T>.asFlux(): IFluxStep<T> = IdentityStep<T>().also { connect(it) }
fun <T> IFluxStep<T>.identity(): IFluxStep<T> = IdentityStep<T>().also { connect(it) }
fun <T> IMonoStep<T>.identity(): IMonoStep<T> = IdentityStep<T>().also { connect(it) }
