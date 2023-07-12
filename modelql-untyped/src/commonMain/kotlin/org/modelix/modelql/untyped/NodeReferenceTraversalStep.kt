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
import org.modelix.model.api.SerializedNodeReference
import org.modelix.model.api.serialize
import org.modelix.modelql.core.IFluxStep
import org.modelix.modelql.core.IMonoStep
import org.modelix.modelql.core.IStep
import org.modelix.modelql.core.IStepOutput
import org.modelix.modelql.core.MonoTransformingStep
import org.modelix.modelql.core.QueryDeserializationContext
import org.modelix.modelql.core.StepDescriptor
import org.modelix.modelql.core.stepOutputSerializer

class NodeReferenceTraversalStep() : MonoTransformingStep<INode, SerializedNodeReference>() {
    override fun transform(input: INode): SerializedNodeReference {
        return SerializedNodeReference((input.reference.serialize()))
    }

    override fun getOutputSerializer(serializersModule: SerializersModule): KSerializer<out IStepOutput<SerializedNodeReference>> {
        return serializersModule.serializer<SerializedNodeReference>().stepOutputSerializer()
    }

    override fun createDescriptor() = Descriptor()

    @Serializable
    @SerialName("untyped.nodeReference")
    class Descriptor() : StepDescriptor() {
        override fun createStep(context: QueryDeserializationContext): IStep {
            return NodeReferenceTraversalStep()
        }
    }

    override fun toString(): String {
        return """${getProducers().single()}.nodeReference()"""
    }
}

fun IMonoStep<INode>.nodeReference() = NodeReferenceTraversalStep().connectAndDowncast(this)
fun IFluxStep<INode>.nodeReference() = NodeReferenceTraversalStep().connectAndDowncast(this)
