/*
 * Copyright (c) 2023.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * https://www.apache.org/licenses/LICENSE-2.0
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
import org.modelix.model.api.getDescendants
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

class DescendantsTraversalStep(val includeSelf: Boolean) : FluxTransformingStep<INode, INode>(), IFluxStep<INode> {
    override fun createFlow(input: StepFlow<INode>, context: IFlowInstantiationContext): StepFlow<INode> {
        return input.flatMapConcat { it.value.getDescendantsAsFlow(includeSelf) }.asStepFlow(this)
    }

    override fun createSequence(evaluationContext: QueryEvaluationContext, queryInput: Sequence<Any?>): Sequence<INode> {
        return getProducer().createSequence(evaluationContext, queryInput).flatMap { it.getDescendants(includeSelf) }
    }

    override fun getOutputSerializer(serializersModule: SerializersModule): KSerializer<out IStepOutput<INode>> {
        return serializersModule.serializer<INode>().stepOutputSerializer(this)
    }

    override fun createDescriptor(context: QueryGraphDescriptorBuilder) = if (includeSelf) WithSelfDescriptor() else WithoutSelfDescriptor()

    @Serializable
    @SerialName("untyped.descendantsAndSelf")
    class WithSelfDescriptor() : StepDescriptor() {
        override fun createStep(context: QueryDeserializationContext): IStep {
            return DescendantsTraversalStep(true)
        }
    }

    @Serializable
    @SerialName("untyped.descendants")
    class WithoutSelfDescriptor() : StepDescriptor() {
        override fun createStep(context: QueryDeserializationContext): IStep {
            return DescendantsTraversalStep(false)
        }
    }

    override fun toString(): String {
        return """${getProducers().single()}.${if (includeSelf) "descendantsAndSelf" else "descendants"}()"""
    }
}

fun IProducingStep<INode>.descendants(includeSelf: Boolean = false): IFluxStep<INode> = DescendantsTraversalStep(includeSelf).also { connect(it) }
