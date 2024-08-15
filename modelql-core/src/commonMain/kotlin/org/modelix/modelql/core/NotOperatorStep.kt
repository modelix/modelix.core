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

class NotOperatorStep() : SimpleMonoTransformingStep<Boolean, Boolean>() {

    override fun transform(evaluationContext: QueryEvaluationContext, input: Boolean): Boolean {
        return !input
    }

    override fun getOutputSerializer(serializationContext: SerializationContext): KSerializer<out IStepOutput<Boolean>> {
        return serializationContext.serializer<Boolean>().stepOutputSerializer(this)
    }

    override fun createDescriptor(context: QueryGraphDescriptorBuilder) = NotDescriptor()

    @Serializable
    @SerialName("not")
    class NotDescriptor : CoreStepDescriptor() {
        override fun createStep(context: QueryDeserializationContext): IStep {
            return NotOperatorStep()
        }
    }

    override fun toString(): String {
        return "!${getProducers().single()}"
    }
}

operator fun IMonoStep<Boolean>.not(): IMonoStep<Boolean> = NotOperatorStep().also { connect(it) }
