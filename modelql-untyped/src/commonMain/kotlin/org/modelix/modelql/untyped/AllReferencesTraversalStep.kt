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
import kotlinx.coroutines.flow.flatMapConcat
import kotlinx.coroutines.flow.map
import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.modules.SerializersModule
import kotlinx.serialization.serializer
import org.modelix.model.api.INode
import org.modelix.modelql.core.FluxTransformingStep
import org.modelix.modelql.core.IFlowInstantiationContext
import org.modelix.modelql.core.IFluxStep
import org.modelix.modelql.core.IMonoStep
import org.modelix.modelql.core.IProducingStep
import org.modelix.modelql.core.IStep
import org.modelix.modelql.core.StepDescriptor
import org.modelix.modelql.core.connect
import org.modelix.modelql.core.flatMapConcatConcurrent

class AllReferencesTraversalStep() : FluxTransformingStep<INode, INode>(), IMonoStep<INode> {
    override fun createFlow(input: Flow<INode>, context: IFlowInstantiationContext): Flow<INode> {
        return input.flatMapConcat { it.getAllReferenceTargetsAsFlow().map { it.second } }
    }

    override fun createSequence(queryInput: Sequence<Any?>): Sequence<INode> {
        return getProducer().createSequence(queryInput).flatMap { it.getAllReferenceTargets().map { it.second } }
    }

    override fun getOutputSerializer(serializersModule: SerializersModule): KSerializer<INode> {
        return serializersModule.serializer<INode>()
    }

    override fun createDescriptor() = Descriptor()

    @Serializable
    @SerialName("untyped.allReferenceTargets")
    class Descriptor() : StepDescriptor() {
        override fun createStep(): IStep {
            return AllReferencesTraversalStep()
        }
    }

    override fun toString(): String {
        return """${getProducers().single()}.allReferences()"""
    }
}

fun IProducingStep<INode>.allReferences(): IFluxStep<INode> = AllReferencesTraversalStep().also { connect(it) }
