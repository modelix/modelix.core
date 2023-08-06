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

import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEmpty
import kotlinx.coroutines.flow.single
import kotlinx.coroutines.flow.take
import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.modules.SerializersModule
import kotlinx.serialization.serializer

class IsEmptyStep() : AggregationStep<Any?, Boolean>() {
    override suspend fun aggregate(input: StepFlow<Any?>): IStepOutput<Boolean> {
        return input.take(1).map { false }.onEmpty { emit(false) }.single().asStepOutput(this)
    }

    override fun aggregate(input: Sequence<IStepOutput<Any?>>): IStepOutput<Boolean> = input.none().asStepOutput(this)

    override fun createDescriptor(context: QueryGraphDescriptorBuilder) = Descriptor()

    @Serializable
    @SerialName("isEmpty")
    class Descriptor() : CoreStepDescriptor() {
        override fun createStep(context: QueryDeserializationContext): IStep {
            return IsEmptyStep()
        }
    }

    override fun getOutputSerializer(serializersModule: SerializersModule): KSerializer<out IStepOutput<Boolean>> {
        return serializersModule.serializer<Boolean>().stepOutputSerializer(this)
    }

    override fun toString(): String {
        return "${getProducers().single()}.isEmpty()"
    }
}

fun IProducingStep<Any?>.isEmpty(): IMonoStep<Boolean> = IsEmptyStep().connectAndDowncast(this)
fun IProducingStep<Any?>.isNotEmpty(): IMonoStep<Boolean> = !isEmpty()
