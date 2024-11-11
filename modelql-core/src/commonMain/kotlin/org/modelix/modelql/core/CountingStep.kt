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

import com.badoo.reaktive.single.Single
import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import org.modelix.streams.count

class CountingStep() : AggregationStep<Any?, Int>() {
    override fun aggregate(input: StepStream<Any?>, context: IStreamInstantiationContext): Single<IStepOutput<Int>> {
        return input.count().asStepStream(this)
    }

    override fun createDescriptor(context: QueryGraphDescriptorBuilder) = CountDescriptor()

    @Serializable
    @SerialName("count")
    class CountDescriptor() : CoreStepDescriptor() {
        override fun createStep(context: QueryDeserializationContext): IStep {
            return CountingStep()
        }

        override fun doNormalize(idReassignments: IdReassignments): StepDescriptor = CountDescriptor()
    }

    override fun getOutputSerializer(serializationContext: SerializationContext): KSerializer<out IStepOutput<Int>> {
        return serializationContext.serializer<Int>().stepOutputSerializer(this)
    }

    override fun toString(): String {
        return "${getProducers().single()}\n.count()"
    }
}

fun IProducingStep<*>.count(): IMonoStep<Int> = CountingStep().also { connect(it) }
