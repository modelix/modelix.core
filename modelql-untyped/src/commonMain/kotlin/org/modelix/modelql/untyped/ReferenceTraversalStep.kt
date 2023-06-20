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
import org.modelix.model.api.resolveReferenceLinkOrFallback
import org.modelix.modelql.core.IFlowInstantiationContext
import org.modelix.modelql.core.IFluxStep
import org.modelix.modelql.core.IMonoStep
import org.modelix.modelql.core.IStep
import org.modelix.modelql.core.MonoTransformingStep
import org.modelix.modelql.core.StepDescriptor
import org.modelix.modelql.core.contains
import org.modelix.modelql.core.flatMapConcatConcurrent
import org.modelix.modelql.core.map
import org.modelix.modelql.core.orNull

class ReferenceTraversalStep(val role: String) : MonoTransformingStep<INode, INode>(), IMonoStep<INode> {
    override fun createFlow(input: Flow<INode>, context: IFlowInstantiationContext): Flow<INode> {
        return input.flatMapConcatConcurrent { it.getReferenceTargetAsFlow(it.resolveReferenceLinkOrFallback(role)) }
    }

    override fun getOutputSerializer(serializersModule: SerializersModule): KSerializer<INode> {
        return serializersModule.serializer<INode>()
    }

    override fun createDescriptor() = Descriptor(role)

    @Serializable
    @SerialName("untyped.referenceTarget")
    class Descriptor(val role: String) : StepDescriptor() {
        override fun createStep(): IStep {
            return ReferenceTraversalStep(role)
        }
    }

    override fun toString(): String {
        return """${getProducers().single()}.reference("$role")"""
    }
}

fun IMonoStep<INode>.reference(role: String) = ReferenceTraversalStep(role).connectAndDowncast(this)
fun IFluxStep<INode>.reference(role: String) = ReferenceTraversalStep(role).connectAndDowncast(this)
fun IMonoStep<INode>.referenceOrNull(role: String): IMonoStep<INode?> = reference(role).orNull()
fun IFluxStep<INode>.referenceOrNull(role: String): IFluxStep<INode?> = map { it.referenceOrNull(role) }
