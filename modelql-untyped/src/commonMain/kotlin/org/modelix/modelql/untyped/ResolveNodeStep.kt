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
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.modules.SerializersModule
import kotlinx.serialization.serializer
import org.modelix.model.api.INode
import org.modelix.model.api.INodeReference
import org.modelix.model.api.INodeResolutionScope
import org.modelix.modelql.core.*

class ResolveNodeStep(): MonoTransformingStep<INodeReference, INode>() {
    override fun createFlow(input: Flow<INodeReference>, context: IFlowInstantiationContext): Flow<INode> {
        return input.map {
            val refScope = context.coroutineScope.coroutineContext[INodeResolutionScope]
                ?: throw IllegalStateException("No INodeResolutionScope found in the coroutine context")
            it.resolveIn(refScope) ?: throw IllegalArgumentException("Node not found: $it")
        }
    }

    override fun getOutputSerializer(serializersModule: SerializersModule): KSerializer<INode> {
        return serializersModule.serializer<INode>()
    }

    override fun createDescriptor() = Descriptor()

    @Serializable
    @SerialName("untyped.resolveNode")
    class Descriptor() : StepDescriptor() {
        override fun createStep(): IStep {
            return ResolveNodeStep()
        }
    }

    override fun toString(): String {
        return getProducers().single().toString() + ".resolve()"
    }
}

fun IMonoStep<INodeReference>.resolve(): IMonoStep<INode> = ResolveNodeStep().also { connect(it) }
fun IFluxStep<INodeReference>.resolve(): IFluxStep<INode> = map { it.resolve() }
