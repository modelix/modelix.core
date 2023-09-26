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

import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEmpty
import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

class NullIfEmpty<E>() : MonoTransformingStep<E, E?>() {

    override fun getOutputSerializer(serializationContext: SerializationContext): KSerializer<out IStepOutput<E?>> {
        return MultiplexedOutputSerializer(
            this,
            listOf(
                getProducer().getOutputSerializer(serializationContext).upcast(),
                nullSerializer<E?>().stepOutputSerializer(this).upcast(),
            ),
        )
    }

    override fun createFlow(input: StepFlow<E>, context: IFlowInstantiationContext): StepFlow<E?> {
        val downcast: StepFlow<E?> = input
        return downcast.map { MultiplexedOutput(0, it) }.onEmpty {
            emit(MultiplexedOutput(1, null.asStepOutput(this@NullIfEmpty)))
        }
    }

    override fun createDescriptor(context: QueryGraphDescriptorBuilder) = OrNullDescriptor()

    @Serializable
    @SerialName("orNull")
    class OrNullDescriptor : CoreStepDescriptor() {
        override fun createStep(context: QueryDeserializationContext): IStep {
            return NullIfEmpty<Any?>()
        }
    }

    override fun toString(): String {
        return """${getProducers().single()}.orNull()"""
    }

    override fun canBeEmpty(): Boolean {
        return false
    }
}

fun <Out> IMonoStep<Out>.orNull(): IMonoStep<Out?> = NullIfEmpty<Out>().connectAndDowncast(this)
fun <Out> IFluxStep<Out>.orNull(): IFluxStep<Out?> = NullIfEmpty<Out>().connectAndDowncast(this)

fun <Out> IMonoStep<Out>.nullIfEmpty(): IMonoStep<Out?> = NullIfEmpty<Out>().connectAndDowncast(this)
fun <Out> IFluxStep<Out>.nullIfEmpty(): IFluxStep<Out?> = NullIfEmpty<Out>().connectAndDowncast(this)
