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

import com.badoo.reaktive.maybe.map
import com.badoo.reaktive.observable.flatMapMaybe
import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import org.modelix.model.api.INode
import org.modelix.model.api.async.asAsyncNode
import org.modelix.modelql.core.IFluxStep
import org.modelix.modelql.core.IMonoStep
import org.modelix.modelql.core.IStep
import org.modelix.modelql.core.IStepOutput
import org.modelix.modelql.core.IStreamInstantiationContext
import org.modelix.modelql.core.IdReassignments
import org.modelix.modelql.core.MonoTransformingStep
import org.modelix.modelql.core.QueryDeserializationContext
import org.modelix.modelql.core.QueryGraphDescriptorBuilder
import org.modelix.modelql.core.SerializationContext
import org.modelix.modelql.core.StepDescriptor
import org.modelix.modelql.core.StepStream
import org.modelix.modelql.core.asStepStream
import org.modelix.modelql.core.stepOutputSerializer

class ParentTraversalStep() : MonoTransformingStep<INode, INode>(), IMonoStep<INode> {

    override fun createStream(input: StepStream<INode>, context: IStreamInstantiationContext): StepStream<INode> {
        return input.flatMapMaybe {
            it.value.asAsyncNode().getParent().map { it.asRegularNode() }
        }.asStepStream(this)
    }

    override fun canBeEmpty(): Boolean = true

    override fun canBeMultiple(): Boolean = getProducer().canBeMultiple()

    override fun getOutputSerializer(serializationContext: SerializationContext): KSerializer<out IStepOutput<INode>> {
        return serializationContext.serializer<INode>().stepOutputSerializer(this)
    }

    override fun createDescriptor(context: QueryGraphDescriptorBuilder) = Descriptor()

    @Serializable
    @SerialName("untyped.parent")
    class Descriptor() : StepDescriptor() {
        override fun createStep(context: QueryDeserializationContext): IStep {
            return ParentTraversalStep()
        }

        override fun doNormalize(idReassignments: IdReassignments): StepDescriptor = Descriptor()
    }

    override fun toString(): String {
        return "${getProducers().single()}\n.parent()"
    }
}

fun IMonoStep<INode>.parent() = ParentTraversalStep().connectAndDowncast(this)
fun IFluxStep<INode>.parent() = ParentTraversalStep().connectAndDowncast(this)
