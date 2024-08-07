/*
 * Copyright (c) 2024.
 *
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

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flatMapConcat
import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import org.modelix.model.api.INode
import org.modelix.model.api.IProperty
import org.modelix.modelql.core.FluxTransformingStep
import org.modelix.modelql.core.IFlowInstantiationContext
import org.modelix.modelql.core.IFluxStep
import org.modelix.modelql.core.IProducingStep
import org.modelix.modelql.core.IStep
import org.modelix.modelql.core.IStepOutput
import org.modelix.modelql.core.QueryDeserializationContext
import org.modelix.modelql.core.QueryGraphDescriptorBuilder
import org.modelix.modelql.core.SerializationContext
import org.modelix.modelql.core.StepDescriptor
import org.modelix.modelql.core.StepFlow
import org.modelix.modelql.core.asStepFlow
import org.modelix.modelql.core.connect
import org.modelix.modelql.core.stepOutputSerializer

class AllPropertiesTraversalStep : FluxTransformingStep<INode, Pair<IProperty, String?>>() {
    @OptIn(ExperimentalCoroutinesApi::class)
    override fun createFlow(
        input: StepFlow<INode>,
        context: IFlowInstantiationContext,
    ): StepFlow<Pair<IProperty, String?>> {
        return input.flatMapConcat { it.value.getAllPropertiesAsFlow() }.asStepFlow(this)
    }

    override fun getOutputSerializer(serializationContext: SerializationContext): KSerializer<out IStepOutput<Pair<IProperty, String?>>> {
        return serializationContext.serializer<Pair<IProperty, String?>>().stepOutputSerializer(this)
    }

    override fun createDescriptor(context: QueryGraphDescriptorBuilder): StepDescriptor = AllPropertiesStepDescriptor()

    @Serializable
    @SerialName("untyped.allProperties")
    class AllPropertiesStepDescriptor : StepDescriptor() {
        override fun createStep(context: QueryDeserializationContext): IStep {
            return AllPropertiesTraversalStep()
        }
    }

    override fun toString(): String {
        return """${getProducer()}.allProperties()"""
    }
}

fun IProducingStep<INode>.allProperties(): IFluxStep<Pair<IProperty, String?>> =
    AllPropertiesTraversalStep().also { connect(it) }
