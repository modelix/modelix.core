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

import kotlinx.coroutines.flow.Flow
import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.modules.SerializersModule
import kotlinx.serialization.serializer
import org.modelix.model.api.INode
import org.modelix.modelql.core.FluxTransformingStep
import org.modelix.modelql.core.IFlowInstantiationContext
import org.modelix.modelql.core.IFluxStep
import org.modelix.modelql.core.IProducingStep
import org.modelix.modelql.core.IStep
import org.modelix.modelql.core.StepDescriptor
import org.modelix.modelql.core.connect
import org.modelix.modelql.core.flatMapConcatConcurrent

class DescendantsTraversalStep(val includeSelf: Boolean) : FluxTransformingStep<INode, INode>(), IFluxStep<INode> {
    override fun createFlow(input: Flow<INode>, context: IFlowInstantiationContext): Flow<INode> {
        return input.flatMapConcatConcurrent { it.getDescendantsAsFlow(includeSelf) }
    }

    override fun getOutputSerializer(serializersModule: SerializersModule): KSerializer<INode> {
        return serializersModule.serializer<INode>()
    }

    override fun createDescriptor() = if (includeSelf) WithSelfDescriptor() else WithoutSelfDescriptor()

    @Serializable
    @SerialName("untyped.descendantsAndSelf")
    class WithSelfDescriptor() : StepDescriptor() {
        override fun createStep(): IStep {
            return DescendantsTraversalStep(true)
        }
    }

    @Serializable
    @SerialName("untyped.descendants")
    class WithoutSelfDescriptor() : StepDescriptor() {
        override fun createStep(): IStep {
            return DescendantsTraversalStep(false)
        }
    }

    override fun toString(): String {
        return """${getProducers().single()}.${if (includeSelf) "descendantsAndSelf" else "descendants"}()"""
    }
}

fun IProducingStep<INode>.descendants(includeSelf: Boolean = false): IFluxStep<INode> = DescendantsTraversalStep(includeSelf).also { connect(it) }
