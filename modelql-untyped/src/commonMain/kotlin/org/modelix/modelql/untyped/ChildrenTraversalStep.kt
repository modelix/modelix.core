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
import org.modelix.model.api.resolveChildLinkOrFallback
import org.modelix.modelql.core.*
import org.modelix.modelql.streams.IConsumer

class ChildrenTraversalStep(val role: String?): AsyncTransformingStep<INode, INode>(), IFluxStep<INode> {
    override fun transformAsync(inputElement: INode, outputConsumer: IConsumer<INode>) {
        inputElement.asAsyncNode().visitChildren(inputElement.resolveChildLinkOrFallback(role), ConsumerAdapter(outputConsumer))
    }

    override fun getOutputSerializer(serializersModule: SerializersModule): KSerializer<INode> {
        return serializersModule.serializer<INode>()
    }

    override fun createDescriptor() = ChildrenStepDescriptor(role)

    @Serializable
    @SerialName("untyped.children")
    class ChildrenStepDescriptor(val role: String?) : StepDescriptor() {
        override fun createStep(): IStep {
            return ChildrenTraversalStep(role)
        }
    }

    override fun toString(): String {
        return """${getProducers().single()}.children("$role")"""
    }
}

fun IProducingStep<INode>.children(role: String?): IFluxStep<INode> = ChildrenTraversalStep(role).also { connect(it) }