/*
 * Copyright (c) 2024.
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

package org.modelix.modelql.core

import com.badoo.reaktive.observable.asObservable
import com.badoo.reaktive.observable.flatMap
import com.badoo.reaktive.observable.map
import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

class ListAsFluxStep<E> : FluxTransformingStep<List<E>, E>() {
    override fun createStream(input: StepStream<List<E>>, context: IStreamInstantiationContext): StepStream<E> {
        return input.flatMap {
            when (it) {
                is CollectorStepOutput<*, *, *> -> {
                    (it as CollectorStepOutput<E, List<IStepOutput<E>>, List<E>>).input.asObservable()
                }
                else -> it.value.asObservable().map {
                    when (it) {
                        is IStepOutput<*> -> it as IStepOutput<E>
                        else -> it.asStepOutput(this)
                    }
                }
            }
        }
    }

    override fun getOutputSerializer(serializationContext: SerializationContext): KSerializer<out IStepOutput<E>> {
        val inputSerializer = getProducer().getOutputSerializer(serializationContext).let {
            when (it) {
                is MultiplexedOutputSerializer<*> -> it.serializers.first()
                else -> it
            }
        }
        return when (inputSerializer) {
            is ListCollectorStepOutputSerializer<*> -> (inputSerializer as ListCollectorStepOutputSerializer<E>).inputElementSerializer
            else -> throw UnsupportedOperationException("Cannot serialize elements of ${getProducer()}")
        }
    }

    override fun toString(): String {
        return "${getProducer()}\n.toFlux()"
    }

    override fun createDescriptor(context: QueryGraphDescriptorBuilder) = Descriptor()

    @Serializable
    @SerialName("listToFlux")
    class Descriptor() : CoreStepDescriptor() {
        override fun createStep(context: QueryDeserializationContext): IStep {
            return ListAsFluxStep<Any?>()
        }

        override fun doNormalize(idReassignments: IdReassignments): StepDescriptor = Descriptor()
    }
}

fun <T> IMonoStep<List<T>>.toFlux(): IFluxStep<T> = ListAsFluxStep<T>().also { connect(it) }
