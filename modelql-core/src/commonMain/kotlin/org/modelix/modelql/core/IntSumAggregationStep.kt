/*
 * Copyright (c) 2024.
 *
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
import org.modelix.streams.fold

class IntSumAggregationStep : AggregationStep<Int, Int>() {
    override fun aggregate(input: StepFlow<Int>, context: IFlowInstantiationContext): Single<IStepOutput<Int>> {
        return input.fold(0) { acc, it -> acc + it.value }.asStepFlow(this)
    }

    override fun createDescriptor(context: QueryGraphDescriptorBuilder) = Descriptor()

    @Serializable
    @SerialName("intSumAggregation")
    class Descriptor() : CoreStepDescriptor() {
        override fun createStep(context: QueryDeserializationContext): IStep {
            return IntSumAggregationStep()
        }

        override fun doNormalize(idReassignments: IdReassignments): StepDescriptor = Descriptor()
    }

    override fun getOutputSerializer(serializationContext: SerializationContext): KSerializer<out IStepOutput<Int>> {
        return serializationContext.serializer<Int>().stepOutputSerializer(this)
    }

    override fun toString(): String {
        return "${getProducer()}\n.sum()"
    }
}

fun IFluxStep<Int>.sum(): IMonoStep<Int> = IntSumAggregationStep().also { connect(it) }
fun IMonoStep<Int>.sum(other: IMonoStep<Int>): IMonoStep<Int> = this.plus(other).sum()
