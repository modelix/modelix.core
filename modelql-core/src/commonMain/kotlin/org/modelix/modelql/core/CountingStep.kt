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

import kotlinx.coroutines.flow.count
import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.modules.SerializersModule
import kotlinx.serialization.serializer

class CountingStep() : AggregationStep<Any?, Int>() {
    override suspend fun aggregate(input: StepFlow<Any?>): IStepOutput<Int> {
        return input.count().asStepOutput(this)
    }

    override fun aggregate(input: Sequence<IStepOutput<Any?>>): IStepOutput<Int> = input.count().asStepOutput(this)

    override fun createDescriptor(context: QueryGraphDescriptorBuilder) = CountDescriptor()

    @Serializable
    @SerialName("count")
    class CountDescriptor() : CoreStepDescriptor() {
        override fun createStep(context: QueryDeserializationContext): IStep {
            return CountingStep()
        }
    }

    override fun getOutputSerializer(serializersModule: SerializersModule): KSerializer<out IStepOutput<Int>> {
        return serializersModule.serializer<Int>().stepOutputSerializer(this)
    }

    override fun toString(): String {
        return "${getProducers().single()}.count()"
    }
}

fun IFluxStep<*>.count(): IMonoStep<Int> = CountingStep().also { connect(it) }
