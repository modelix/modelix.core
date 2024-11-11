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

import com.badoo.reaktive.observable.map
import com.badoo.reaktive.single.Single
import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import org.modelix.model.api.INode
import org.modelix.model.api.remove
import org.modelix.modelql.core.AggregationStep
import org.modelix.modelql.core.IMonoStep
import org.modelix.modelql.core.IProducingStep
import org.modelix.modelql.core.IStep
import org.modelix.modelql.core.IStepOutput
import org.modelix.modelql.core.IStreamInstantiationContext
import org.modelix.modelql.core.IdReassignments
import org.modelix.modelql.core.QueryDeserializationContext
import org.modelix.modelql.core.QueryGraphDescriptorBuilder
import org.modelix.modelql.core.SerializationContext
import org.modelix.modelql.core.StepDescriptor
import org.modelix.modelql.core.StepStream
import org.modelix.modelql.core.asStepStream
import org.modelix.modelql.core.connect
import org.modelix.modelql.core.stepOutputSerializer
import org.modelix.streams.count

class RemoveNodeStep() : AggregationStep<INode, Int>() {

    override fun getOutputSerializer(serializationContext: SerializationContext): KSerializer<out IStepOutput<Int>> {
        return serializationContext.serializer<Int>().stepOutputSerializer(this)
    }

    override fun aggregate(input: StepStream<INode>, context: IStreamInstantiationContext): Single<IStepOutput<Int>> {
        return input.map { it.value.remove() }.count().asStepStream(this)
    }

    override fun createDescriptor(context: QueryGraphDescriptorBuilder): StepDescriptor {
        return Descriptor()
    }

    override fun requiresWriteAccess(): Boolean {
        return true
    }

    override fun toString(): String {
        return "${getProducer()}\n.remove()"
    }

    @Serializable
    @SerialName("untyped.remove")
    class Descriptor() : StepDescriptor() {
        override fun createStep(context: QueryDeserializationContext): IStep {
            return RemoveNodeStep()
        }

        override fun doNormalize(idReassignments: IdReassignments): StepDescriptor = Descriptor()
    }
}

fun IProducingStep<INode>.remove(): IMonoStep<Int> {
    return RemoveNodeStep().also { connect(it) }
}
