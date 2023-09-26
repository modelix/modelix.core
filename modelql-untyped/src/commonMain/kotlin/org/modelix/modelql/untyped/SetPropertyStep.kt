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
import org.modelix.model.api.INode
import org.modelix.model.api.IProperty
import org.modelix.model.api.key
import org.modelix.model.api.resolvePropertyOrFallback
import org.modelix.modelql.core.IMonoStep
import org.modelix.modelql.core.IStep
import org.modelix.modelql.core.IStepOutput
import org.modelix.modelql.core.QueryDeserializationContext
import org.modelix.modelql.core.QueryGraphDescriptorBuilder
import org.modelix.modelql.core.SerializationContext
import org.modelix.modelql.core.StepDescriptor
import org.modelix.modelql.core.TransformingStepWithParameter
import org.modelix.modelql.core.asMono
import org.modelix.modelql.core.connect

class SetPropertyStep(val role: String) :
    TransformingStepWithParameter<INode, String?, Any?, INode>() {
    override fun getOutputSerializer(serializationContext: SerializationContext): KSerializer<out IStepOutput<INode>> {
        return getInputProducer().getOutputSerializer(serializationContext)
    }

    override fun transformElement(input: IStepOutput<INode>, parameter: IStepOutput<String?>?): IStepOutput<INode> {
        input.value.setPropertyValue(input.value.resolvePropertyOrFallback(role), parameter?.value)
        return input
    }

    override fun createDescriptor(context: QueryGraphDescriptorBuilder): StepDescriptor {
        return Descriptor(role)
    }

    override fun requiresWriteAccess(): Boolean {
        return true
    }

    override fun toString(): String {
        return "${getProducer()}.setProperty($role, ${getParameterProducer()})"
    }

    @Serializable
    @SerialName("untyped.setProperty")
    class Descriptor(val role: String) : StepDescriptor() {
        override fun createStep(context: QueryDeserializationContext): IStep {
            return SetPropertyStep(role)
        }
    }
}

fun IMonoStep<INode>.setProperty(role: String, value: String?): IMonoStep<INode> {
    return setProperty(role, value.asMono())
}
fun IMonoStep<INode>.setProperty(role: String, value: IMonoStep<String?>): IMonoStep<INode> {
    return SetPropertyStep(role).also {
        connect(it)
        value.connect(it)
    }
}
fun IMonoStep<INode>.setProperty(role: IProperty, value: String?): IMonoStep<INode> {
    return setProperty(role, value.asMono())
}
fun IMonoStep<INode>.setProperty(role: IProperty, value: IMonoStep<String?>): IMonoStep<INode> {
    return setProperty(role.key(), value)
}
