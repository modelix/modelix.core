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

import kotlinx.serialization.KSerializer
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import org.modelix.kotlin.utils.IMonoStream
import org.modelix.kotlin.utils.IStream

/**
 * Can carry some additional data required for processing the result on the client side.
 */
interface IStepOutput<out E> {
    val value: E
}

fun <T> IStepOutput<*>.upcast(): IStepOutput<T> = this as IStepOutput<T>

typealias StepFlow<E> = IStream<IStepOutput<E>>
typealias MonoStepFlow<E> = IMonoStream<IStepOutput<E>>
val <T> IStream<IStepOutput<T>>.value: IStream<T> get() = map { it.value }
fun <T> IStream<T>.asStepFlow(owner: IProducingStep<T>?): StepFlow<T> = map { it.asStepOutput(owner) }
fun <T> IMonoStream<T>.asStepFlow(owner: IProducingStep<T>?): MonoStepFlow<T> = map { it.asStepOutput(owner) }
fun <T> StepFlow<*>.upcast(): StepFlow<T> = this as StepFlow<T>

class SimpleStepOutput<out E>(override val value: E, val owner: IProducingStep<E>?) : IStepOutput<E> {
    override fun toString(): String {
        return "SimpleStepOutput[$value]"
    }
}

class SimpleStepOutputSerializer<E>(val valueSerializer: KSerializer<E>, val owner: IProducingStep<E>?) : KSerializer<SimpleStepOutput<E>> {
    init {
        require(valueSerializer !is SimpleStepOutputSerializer<*>)
        require(valueSerializer !is ZipOutputSerializer<*, *>)
    }
    override fun deserialize(decoder: Decoder): SimpleStepOutput<E> {
        return SimpleStepOutput(valueSerializer.deserialize(decoder), owner)
    }

    override val descriptor: SerialDescriptor
        get() = valueSerializer.descriptor

    override fun serialize(encoder: Encoder, value: SimpleStepOutput<E>) {
        valueSerializer.serialize(encoder, value.value)
    }
}

fun <T> KSerializer<T>.stepOutputSerializer(owner: IProducingStep<T>?): SimpleStepOutputSerializer<T> = SimpleStepOutputSerializer(this, owner)

fun <T> T.asStepOutput(owner: IProducingStep<T>?): IStepOutput<T> = SimpleStepOutput(this, owner)
