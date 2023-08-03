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
import kotlinx.serialization.builtins.nullable
import kotlinx.serialization.modules.SerializersModule
import kotlinx.serialization.serializer
import org.modelix.model.api.INodeReference
import org.modelix.model.api.SerializedNodeReference
import org.modelix.model.api.serialize
import org.modelix.modelql.core.ConstantSourceStep
import org.modelix.modelql.core.IMonoStep
import org.modelix.modelql.core.IStep
import org.modelix.modelql.core.IStepOutput
import org.modelix.modelql.core.QueryDeserializationContext
import org.modelix.modelql.core.QueryGraphDescriptorBuilder
import org.modelix.modelql.core.StepDescriptor
import org.modelix.modelql.core.stepOutputSerializer
import kotlin.jvm.JvmName
import kotlin.reflect.typeOf

class NodeReferenceSourceStep(element: INodeReference?) : ConstantSourceStep<INodeReference?>(element, typeOf<INodeReference?>()) {
    override fun getOutputSerializer(serializersModule: SerializersModule): KSerializer<out IStepOutput<INodeReference?>> {
        return serializersModule.serializer<INodeReference>().nullable.stepOutputSerializer(this)
    }

    override fun createDescriptor(context: QueryGraphDescriptorBuilder) = Descriptor(element)

    @Serializable
    @SerialName("nodeReferenceMonoSource")
    class Descriptor(val element: INodeReference?) : StepDescriptor() {
        override fun createStep(context: QueryDeserializationContext): IStep {
            return NodeReferenceSourceStep(element)
        }
    }

    override fun toString(): String {
        return "<${element?.serialize()}>"
    }

    override fun canEvaluateStatically(): Boolean {
        return true
    }

    override fun evaluateStatically(): INodeReference? {
        return element
    }
}

@JvmName("asMonoNullable")
fun INodeReference?.asMono(): IMonoStep<INodeReference?> {
    return NodeReferenceSourceStep(this?.serialize()?.let { SerializedNodeReference(it) })
}
fun INodeReference.asMono(): IMonoStep<INodeReference> {
    return NodeReferenceSourceStep(SerializedNodeReference(serialize())) as IMonoStep<INodeReference>
}
