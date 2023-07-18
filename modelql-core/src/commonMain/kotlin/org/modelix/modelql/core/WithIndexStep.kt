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
import kotlinx.coroutines.flow.withIndex
import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.modules.SerializersModule

class WithIndexStep<E> : MonoTransformingStep<E, IZip2Output<Any?, E, Int>>() {
    override fun requiresSingularQueryInput(): Boolean {
        return true
    }

    override fun getOutputSerializer(serializersModule: SerializersModule): KSerializer<out IStepOutput<IZip2Output<Any?, E, Int>>> {
        return ZipOutputSerializer(
            arrayOf(
                getProducer().getOutputSerializer(serializersModule).upcast(),
                Int.serializer().stepOutputSerializer().upcast()
            )
        )
    }

    override fun transform(input: E): IZip2Output<Any?, E, Int> {
        throw UnsupportedOperationException()
    }

    override fun createFlow(input: StepFlow<E>, context: IFlowInstantiationContext): StepFlow<IZip2Output<Any?, E, Int>> {
        return input.withIndex().map { ZipStepOutput(listOf(it.value, it.index.asStepOutput())) }
    }

    override fun createTransformingSequence(input: Sequence<E>): Sequence<IZip2Output<Any?, E, Int>> {
        return input.mapIndexed { index, value -> ZipNOutput(listOf(value, index)) as IZip2Output<Any?, E, Int> }
    }

    override fun evaluate(queryInput: Any?): Optional<IZip2Output<Any?, E, Int>> {
        return getProducer().evaluate(queryInput).map { transform(it) }
    }

    override fun createDescriptor(context: QuerySerializationContext): StepDescriptor {
        return Descriptor()
    }

    @Serializable
    @SerialName("withIndex")
    class Descriptor : CoreStepDescriptor() {
        override fun createStep(context: QueryDeserializationContext): IStep {
            return WithIndexStep<Any?>()
        }
    }

    override fun toString(): String {
        return "${getProducers().single()}.withIndex()"
    }
}

typealias IWithIndex<V> = IZip2Output<Any?, V, Int>

fun <T> IFluxStep<T>.withIndex(): IFluxStep<IWithIndex<T>> = WithIndexStep<T>().connectAndDowncast(this)
val IMonoStep<IWithIndex<*>>.index: IMonoStep<Int> get() = second
val <T> IMonoStep<IWithIndex<T>>.value: IMonoStep<T> get() = first
