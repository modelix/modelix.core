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

import kotlinx.coroutines.flow.flatMapConcat
import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.modules.SerializersModule
import kotlinx.serialization.serializer
import org.modelix.model.api.INode
import org.modelix.model.api.resolveChildLinkOrFallback
import org.modelix.modelql.core.FluxTransformingStep
import org.modelix.modelql.core.IFlowInstantiationContext
import org.modelix.modelql.core.IFluxStep
import org.modelix.modelql.core.IProducingStep
import org.modelix.modelql.core.IStep
import org.modelix.modelql.core.IStepOutput
import org.modelix.modelql.core.QueryDeserializationContext
import org.modelix.modelql.core.QueryEvaluationContext
import org.modelix.modelql.core.QueryGraphDescriptorBuilder
import org.modelix.modelql.core.StepDescriptor
import org.modelix.modelql.core.StepFlow
import org.modelix.modelql.core.asStepFlow
import org.modelix.modelql.core.connect
import org.modelix.modelql.core.stepOutputSerializer

class ChildrenTraversalStep(val role: String?) : FluxTransformingStep<INode, INode>(), IFluxStep<INode> {
    override fun createFlow(input: StepFlow<INode>, context: IFlowInstantiationContext): StepFlow<INode> {
        return input.flatMapConcat { it.value.getChildrenAsFlow(it.value.resolveChildLinkOrFallback(role)) }.asStepFlow()
    }

    override fun createSequence(evaluationContext: QueryEvaluationContext, queryInput: Sequence<Any?>): Sequence<INode> {
        return getProducer().createSequence(evaluationContext, queryInput).flatMap { it.getChildren(it.resolveChildLinkOrFallback(role)) }
    }

    override fun getOutputSerializer(serializersModule: SerializersModule): KSerializer<out IStepOutput<INode>> {
        return serializersModule.serializer<INode>().stepOutputSerializer()
    }

    override fun createDescriptor(context: QueryGraphDescriptorBuilder) = ChildrenStepDescriptor(role)

    @Serializable
    @SerialName("untyped.children")
    class ChildrenStepDescriptor(val role: String?) : StepDescriptor() {
        override fun createStep(context: QueryDeserializationContext): IStep {
            return ChildrenTraversalStep(role)
        }
    }

    override fun toString(): String {
        return """${getProducers().single()}.children("$role")"""
    }
}

fun IProducingStep<INode>.children(role: String?): IFluxStep<INode> = ChildrenTraversalStep(role).also { connect(it) }
