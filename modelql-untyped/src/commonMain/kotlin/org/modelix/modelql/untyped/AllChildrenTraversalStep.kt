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
import org.modelix.model.api.asAsyncNode
import org.modelix.modelql.core.*

class AllChildrenTraversalStep(): AsyncTransformingStep<INode, INode>(), IFluxStep<INode> {
    override fun transformAsync(inputElement: INode, outputConsumer: IConsumer<INode>) {
        inputElement.asAsyncNode().visitAllChildren(ConsumerAdapter(outputConsumer))
    }

    override fun getOutputSerializer(serializersModule: SerializersModule): KSerializer<INode> {
        return serializersModule.serializer<INode>()
    }

    override fun createDescriptor() = AllChildrenStepDescriptor()

    @Serializable
    @SerialName("untyped.allChildren")
    class AllChildrenStepDescriptor() : StepDescriptor() {
        override fun createStep(): IStep {
            return AllChildrenTraversalStep()
        }
    }

    override fun toString(): String {
        return """${getProducers().single()}.allChildren()"""
    }
}

fun IProducingStep<INode>.allChildren(): IFluxStep<INode> = AllChildrenTraversalStep().also { connect(it) }
