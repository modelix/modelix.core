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
import org.modelix.model.api.ConceptReference
import org.modelix.modelql.core.IFluxStep
import org.modelix.modelql.core.IMonoStep
import org.modelix.modelql.core.IStep
import org.modelix.modelql.core.MonoTransformingStep
import org.modelix.modelql.core.StepDescriptor

class ConceptReferenceUIDTraversalStep() : MonoTransformingStep<ConceptReference, String>() {
    override fun transform(input: ConceptReference): String {
        return input.getUID()
    }

    override fun getOutputSerializer(serializersModule: SerializersModule): KSerializer<String> {
        return serializersModule.serializer<String>()
    }

    override fun createDescriptor() = Descriptor()

    @Serializable
    @SerialName("conceptReference.uid")
    class Descriptor() : StepDescriptor() {
        override fun createStep(): IStep {
            return ConceptReferenceUIDTraversalStep()
        }
    }

    override fun toString(): String {
        return """${getProducers().single()}.getUID()"""
    }
}

fun IMonoStep<ConceptReference>.getUID() = ConceptReferenceUIDTraversalStep().connectAndDowncast(this)
fun IFluxStep<ConceptReference>.getUID() = ConceptReferenceUIDTraversalStep().connectAndDowncast(this)
