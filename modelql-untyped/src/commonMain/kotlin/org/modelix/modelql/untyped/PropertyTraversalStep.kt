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
import kotlinx.coroutines.flow.onEmpty
import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.modules.SerializersModule
import kotlinx.serialization.serializer
import org.modelix.model.api.INode
import org.modelix.model.api.asAsyncNode
import org.modelix.model.api.resolvePropertyOrFallback
import org.modelix.modelql.core.*

class PropertyTraversalStep(val role: String): MonoTransformingStep<INode, String?>(), IMonoStep<String?> {
    override fun createFlow(input: Flow<INode>, context: IFlowInstantiationContext): Flow<String?> {
        return input.flatMapConcat { it.getPropertyValueAsFlow(it.resolvePropertyOrFallback(role)) }
    }

    override fun getOutputSerializer(serializersModule: SerializersModule): KSerializer<String?> {
        return serializersModule.serializer<String?>()
    }

    override fun createDescriptor() = PropertyStepDescriptor(role)

    @Serializable
    @SerialName("untyped.property")
    class PropertyStepDescriptor(val role: String) : StepDescriptor() {
        override fun createStep(): IStep {
            return PropertyTraversalStep(role)
        }
    }

    override fun toString(): String {
        return """${getProducers().single()}.property("$role")"""
    }
}

fun IMonoStep<INode>.property(role: String): IMonoStep<String?> = PropertyTraversalStep(role).also { connect(it) }
fun IFluxStep<INode>.property(role: String): IFluxStep<String?> = map { it.property(role) }