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
import kotlinx.serialization.builtins.nullable
import org.modelix.model.api.INode
import org.modelix.modelql.core.IFluxStep
import org.modelix.modelql.core.IMonoStep
import org.modelix.modelql.core.IStep
import org.modelix.modelql.core.IStepOutput
import org.modelix.modelql.core.QueryDeserializationContext
import org.modelix.modelql.core.QueryEvaluationContext
import org.modelix.modelql.core.QueryGraphDescriptorBuilder
import org.modelix.modelql.core.SerializationContext
import org.modelix.modelql.core.SimpleMonoTransformingStep
import org.modelix.modelql.core.StepDescriptor
import org.modelix.modelql.core.stepOutputSerializer

class RoleInParentTraversalStep() : SimpleMonoTransformingStep<INode, String?>() {
    override fun transform(evaluationContext: QueryEvaluationContext, input: INode): String? {
        return input.roleInParent
    }

    override fun getOutputSerializer(serializationContext: SerializationContext): KSerializer<out IStepOutput<String?>> {
        return serializationContext.serializer<String>().nullable.stepOutputSerializer(this)
    }

    override fun createDescriptor(context: QueryGraphDescriptorBuilder) = Descriptor()

    @Serializable
    @SerialName("untyped.roleInParent")
    class Descriptor() : StepDescriptor() {
        override fun createStep(context: QueryDeserializationContext): IStep {
            return RoleInParentTraversalStep()
        }
    }

    override fun toString(): String {
        return """${getProducers().single()}.roleInParent()"""
    }
}

@Deprecated("use .linkInParent()", ReplaceWith("linkInParent()"))
fun IMonoStep<INode>.roleInParent() = RoleInParentTraversalStep().connectAndDowncast(this)

@Deprecated("use .linkInParent()", ReplaceWith("linkInParent()"))
fun IFluxStep<INode>.roleInParent() = RoleInParentTraversalStep().connectAndDowncast(this)
