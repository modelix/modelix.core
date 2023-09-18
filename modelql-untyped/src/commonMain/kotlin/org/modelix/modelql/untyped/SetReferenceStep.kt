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

import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.modules.SerializersModule
import org.modelix.model.api.INode
import org.modelix.model.api.IReferenceLink
import org.modelix.model.api.key
import org.modelix.model.api.resolveReferenceLinkOrFallback
import org.modelix.modelql.core.IMonoStep
import org.modelix.modelql.core.IStep
import org.modelix.modelql.core.IStepOutput
import org.modelix.modelql.core.QueryDeserializationContext
import org.modelix.modelql.core.QueryGraphDescriptorBuilder
import org.modelix.modelql.core.StepDescriptor
import org.modelix.modelql.core.TransformingStepWithParameter
import org.modelix.modelql.core.connect

class SetReferenceStep(val role: String) :
    TransformingStepWithParameter<INode, INode?, INode?, INode>() {

    override fun getOutputSerializer(serializersModule: SerializersModule): KSerializer<out IStepOutput<INode>> {
        return getInputProducer().getOutputSerializer(serializersModule)
    }

    override fun transformElement(input: IStepOutput<INode>, parameter: IStepOutput<INode?>?): IStepOutput<INode> {
        input.value.setReferenceTarget(input.value.resolveReferenceLinkOrFallback(role), parameter?.value)
        return input
    }

    override fun createDescriptor(context: QueryGraphDescriptorBuilder): StepDescriptor {
        return Descriptor(role)
    }

    override fun requiresWriteAccess(): Boolean {
        return true
    }

    override fun toString(): String {
        return "${getProducer()}.setReference($role, ${getParameterProducer()})"
    }

    @Serializable
    @SerialName("untyped.setReference")
    class Descriptor(val role: String) : StepDescriptor() {
        override fun createStep(context: QueryDeserializationContext): IStep {
            return SetReferenceStep(role)
        }
    }
}

fun IMonoStep<INode>.setReference(role: String, target: IMonoStep<INode?>): IMonoStep<INode> {
    return SetReferenceStep(role).also {
        connect(it)
        target.connect(it)
    }
}
fun IMonoStep<INode>.setReference(role: IReferenceLink, target: IMonoStep<INode?>): IMonoStep<INode> {
    return setReference(role.key(), target)
}
