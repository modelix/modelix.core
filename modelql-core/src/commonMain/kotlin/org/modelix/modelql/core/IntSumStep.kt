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
import kotlinx.serialization.serializer

class IntSumStep(val operand: Int) : MonoTransformingStep<Int, Int>() {

    override fun transform(evaluationContext: QueryEvaluationContext, input: Int): Int {
        return input + operand
    }

    override fun createDescriptor(context: QueryGraphDescriptorBuilder) = IntSumDescriptor(operand)

    @Serializable
    @SerialName("intSum")
    class IntSumDescriptor(val operand: Int) : CoreStepDescriptor() {
        override fun createStep(context: QueryDeserializationContext): IStep {
            return IntSumStep(operand)
        }
    }

    override fun getOutputSerializer(serializersModule: SerializersModule): KSerializer<out IStepOutput<Int>> {
        return serializersModule.serializer<Int>().stepOutputSerializer(this)
    }

    override fun toString(): String {
        return "${getProducer()} + $operand"
    }
}

operator fun IMonoStep<Int>.plus(other: Int) = IntSumStep(other).connectAndDowncast(this)
operator fun IFluxStep<Int>.plus(other: Int) = IntSumStep(other).connectAndDowncast(this)
operator fun Int.plus(other: IMonoStep<Int>): IMonoStep<Int> = other.plus(this)
operator fun Int.plus(other: IFluxStep<Int>): IFluxStep<Int> = other.plus(this)
