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
import org.modelix.model.api.IChildLink
import org.modelix.model.api.IChildLinkReference
import org.modelix.model.api.INode
import org.modelix.modelql.core.IMonoStep
import org.modelix.modelql.core.IStep
import org.modelix.modelql.core.IStepOutput
import org.modelix.modelql.core.IdReassignments
import org.modelix.modelql.core.QueryDeserializationContext
import org.modelix.modelql.core.QueryGraphDescriptorBuilder
import org.modelix.modelql.core.SerializationContext
import org.modelix.modelql.core.StepDescriptor
import org.modelix.modelql.core.TransformingStepWithParameter
import org.modelix.modelql.core.connect

class MoveNodeStep(val link: IChildLinkReference, val index: Int) :
    TransformingStepWithParameter<INode, INode, INode, INode>() {

    override fun getOutputSerializer(serializationContext: SerializationContext): KSerializer<out IStepOutput<INode>> {
        return getInputProducer().getOutputSerializer(serializationContext)
    }

    override fun validate() {
        super.validate()
        require(!getParameterProducer().canBeEmpty()) { "The child parameter for moveChild is mandatory, but was: ${getParameterProducer()}" }
    }

    override fun transformElement(input: IStepOutput<INode>, parameter: IStepOutput<INode>?): IStepOutput<INode> {
        input.value.moveChild(link.toLegacy(), index, parameter!!.value)
        return input
    }

    override fun createDescriptor(context: QueryGraphDescriptorBuilder): StepDescriptor {
        return Descriptor(link.getIdOrNameOrNull(), link, index)
    }

    override fun requiresWriteAccess(): Boolean {
        return true
    }

    override fun toString(): String {
        return "${getProducer()}\n.moveChild($link, $index, ${getParameterProducer()})"
    }

    @Serializable
    @SerialName("untyped.moveChild")
    data class Descriptor(val role: String?, val link: IChildLinkReference? = null, val index: Int) : StepDescriptor() {
        override fun createStep(context: QueryDeserializationContext): IStep {
            return MoveNodeStep(link ?: IChildLinkReference.fromUnclassifiedString(role), index)
        }

        override fun doNormalize(idReassignments: IdReassignments): StepDescriptor = Descriptor(role, link, index)
    }
}

@Deprecated("provide an IChildLinkReference")
fun IMonoStep<INode>.moveChild(link: String?, index: Int = -1, child: IMonoStep<INode>): IMonoStep<INode> {
    return moveChild(IChildLinkReference.fromUnclassifiedString(link), index, child)
}

fun IMonoStep<INode>.moveChild(link: IChildLinkReference, index: Int = -1, child: IMonoStep<INode>): IMonoStep<INode> {
    return MoveNodeStep(link, index).also {
        connect(it)
        child.connect(it)
    }
}

@Deprecated("provide an IChildLinkReference")
fun IMonoStep<INode>.moveChild(link: IChildLink, index: Int = -1, child: IMonoStep<INode>): IMonoStep<INode> {
    return moveChild(link.toReference(), index, child)
}
