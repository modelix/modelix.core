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
package org.modelix.modelql.untyped

import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.modules.SerializersModule
import kotlinx.serialization.serializer
import org.modelix.model.api.INode
import org.modelix.model.api.resolvePropertyOrFallback
import org.modelix.modelql.core.IFluxStep
import org.modelix.modelql.core.IMonoStep
import org.modelix.modelql.core.IStep
import org.modelix.modelql.core.IStepOutput
import org.modelix.modelql.core.MonoTransformingStep
import org.modelix.modelql.core.QueryDeserializationContext
import org.modelix.modelql.core.QueryEvaluationContext
import org.modelix.modelql.core.QueryGraphDescriptorBuilder
import org.modelix.modelql.core.StepDescriptor
import org.modelix.modelql.core.stepOutputSerializer

class PropertyTraversalStep(val role: String) : MonoTransformingStep<INode, String?>(), IMonoStep<String?> {
    override fun transform(evaluationContext: QueryEvaluationContext, input: INode): String? {
        return input.getPropertyValue(input.resolvePropertyOrFallback(role))
    }

    override fun canBeEmpty(): Boolean = getProducer().canBeEmpty()

    override fun canBeMultiple(): Boolean = getProducer().canBeMultiple()

    override fun getOutputSerializer(serializersModule: SerializersModule): KSerializer<out IStepOutput<String?>> {
        return serializersModule.serializer<String?>().stepOutputSerializer()
    }

    override fun createDescriptor(context: QueryGraphDescriptorBuilder) = PropertyStepDescriptor(role)

    @Serializable
    @SerialName("untyped.property")
    class PropertyStepDescriptor(val role: String) : StepDescriptor() {
        override fun createStep(context: QueryDeserializationContext): IStep {
            return PropertyTraversalStep(role)
        }
    }

    override fun toString(): String {
        return """${getProducers().single()}.property("$role")"""
    }
}

fun IMonoStep<INode>.property(role: String) = PropertyTraversalStep(role).connectAndDowncast(this)
fun IFluxStep<INode>.property(role: String) = PropertyTraversalStep(role).connectAndDowncast(this)
