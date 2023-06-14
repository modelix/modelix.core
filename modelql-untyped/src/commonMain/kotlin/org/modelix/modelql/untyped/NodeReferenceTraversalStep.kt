/*
 * Copyright 2003-2023 JetBrains s.r.o.
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

import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.modules.SerializersModule
import kotlinx.serialization.serializer
import org.modelix.model.api.INode
import org.modelix.model.api.INodeReference
import org.modelix.model.api.SerializedNodeReference
import org.modelix.model.api.serialize
import org.modelix.modelql.core.*

class NodeReferenceTraversalStep(): MonoTransformingStep<INode, SerializedNodeReference>() {
    override fun transform(element: INode): Sequence<SerializedNodeReference> {
        return sequenceOf(SerializedNodeReference((element.reference.serialize())))
    }

    override fun getOutputSerializer(serializersModule: SerializersModule): KSerializer<SerializedNodeReference> {
        return serializersModule.serializer<SerializedNodeReference>()
    }

    override fun createDescriptor() = Descriptor()

    @Serializable
    @SerialName("untyped.nodeReference")
    class Descriptor() : StepDescriptor() {
        override fun createStep(): IStep {
            return NodeReferenceTraversalStep()
        }
    }

    override fun toString(): String {
        return """${getProducers().single()}.nodeReference()"""
    }
}

fun IMonoStep<INode>.nodeReference(): IMonoStep<INodeReference> = NodeReferenceTraversalStep().also { connect(it) }
fun IFluxStep<INode>.nodeReference(): IFluxStep<INodeReference> = map { it.nodeReference() }
