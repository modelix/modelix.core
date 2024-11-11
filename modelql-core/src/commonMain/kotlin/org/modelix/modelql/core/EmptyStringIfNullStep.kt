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
package org.modelix.modelql.core

import com.badoo.reaktive.observable.map
import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

class EmptyStringIfNullStep : MonoTransformingStep<String?, String>() {

    override fun getOutputSerializer(serializationContext: SerializationContext): KSerializer<out IStepOutput<String>> {
        val inputSerializer: KSerializer<IStepOutput<String?>> = getProducer().getOutputSerializer(serializationContext).upcast()
        return MultiplexedOutputSerializer<String>(
            this,
            listOf<KSerializer<IStepOutput<String>>>(
                inputSerializer as KSerializer<IStepOutput<String>>,
                serializationContext.serializer<String>().stepOutputSerializer(this).upcast(),
            ),
        )
    }

    override fun createStream(input: StepStream<String?>, context: IStreamInstantiationContext): StepStream<String> {
        return input.map {
            if (it.value == null) {
                MultiplexedOutput(1, "".asStepOutput(this))
            } else {
                MultiplexedOutput(0, it.upcast())
            }
        }
    }

    override fun createDescriptor(context: QueryGraphDescriptorBuilder): StepDescriptor {
        return Descriptor()
    }

    @Serializable
    @SerialName("emptyStringIfNull")
    class Descriptor : CoreStepDescriptor() {
        override fun createStep(context: QueryDeserializationContext): IStep {
            return EmptyStringIfNullStep()
        }

        override fun doNormalize(idReassignments: IdReassignments): StepDescriptor = Descriptor()
    }

    override fun toString(): String {
        return "${getProducers().single()}\n.emptyStringIfNull()"
    }
}

fun IMonoStep<String?>.emptyStringIfNull() = EmptyStringIfNullStep().connectAndDowncast(this)
fun IFluxStep<String?>.emptyStringIfNull() = EmptyStringIfNullStep().connectAndDowncast(this)
