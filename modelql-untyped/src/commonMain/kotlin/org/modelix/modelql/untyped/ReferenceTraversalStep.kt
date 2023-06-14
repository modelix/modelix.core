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
import org.modelix.model.api.resolveReferenceLinkOrFallback
import org.modelix.modelql.core.*

class ReferenceTraversalStep(val role: String): AsyncTransformingStep<INode, INode>(), IMonoStep<INode> {
    override fun transformAsync(inputElement: INode, outputConsumer: IConsumer<INode>) {
        inputElement.asAsyncNode().visitReferenceTarget(inputElement.resolveReferenceLinkOrFallback(role), ConsumerAdapter(outputConsumer))
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

fun IMonoStep<INode>.reference(role: String): IMonoStep<INode> = ReferenceTraversalStep(role).also { connect(it) }
fun IFluxStep<INode>.reference(role: String): IFluxStep<INode> = map { it.reference(role) }
fun IMonoStep<INode>.referenceOrNull(role: String): IMonoStep<INode?> = reference(role).orNull()
fun IFluxStep<INode>.referenceOrNull(role: String): IFluxStep<INode?> = map { it.referenceOrNull(role) }