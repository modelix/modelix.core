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

import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

class SharedStep<E>() : MonoTransformingStep<E, E>() {
    override fun getOutputSerializer(serializationContext: SerializationContext): KSerializer<out IStepOutput<E>> {
        return getProducer().getOutputSerializer(serializationContext)
    }

    override fun createStream(input: StepStream<E>, context: IStreamInstantiationContext): StepStream<E> {
        throw RuntimeException("The stream for shared steps is expected to be created by the query")
    }

    override fun getRootInputSteps(): Set<IStep> {
        return setOf(this)
    }

    override fun createDescriptor(context: QueryGraphDescriptorBuilder): StepDescriptor {
        return Descriptor()
    }

    @Serializable
    @SerialName("shared")
    class Descriptor() : CoreStepDescriptor() {
        override fun createStep(context: QueryDeserializationContext): IStep {
            return SharedStep<Any?>()
        }

        override fun doNormalize(idReassignments: IdReassignments): StepDescriptor = Descriptor()
    }

    override fun toString(): String {
        return "${getProducer()}\n.shared()"
    }
}
