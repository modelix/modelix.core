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
import kotlinx.serialization.builtins.nullable
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.descriptors.buildClassSerialDescriptor
import kotlinx.serialization.encoding.CompositeDecoder
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.encoding.decodeStructure
import kotlinx.serialization.encoding.encodeStructure

class MultiplexedOutput<out E>(val muxIndex: Int, val output: IStepOutput<E>) : IStepOutput<E> {
    override val value: E
        get() = output.value
}

data class MultiplexedOutputSerializer<E>(
    val owner: IStep,
    val serializers: List<KSerializer<IStepOutput<E>>>,
) : KSerializer<MultiplexedOutput<E>> {
    override fun deserialize(decoder: Decoder): MultiplexedOutput<E> {
        try {
            var muxIndex: Int? = null
            var caseOutput: IStepOutput<E>? = null

            decoder.decodeStructure(descriptor) {
                while (true) {
                    val i = decodeElementIndex(descriptor)
                    if (i == CompositeDecoder.DECODE_DONE) break
                    caseOutput = decodeSerializableElement(descriptor, i, serializers[i])
                    muxIndex = i
                }
            }
            return MultiplexedOutput(
                muxIndex ?: throw IllegalStateException("muxIndex missing"),
                caseOutput ?: throw IllegalStateException("output missing"),
            )
        } catch (ex: Exception) {
            throw RuntimeException("Deserialization failed for step $owner", ex)
        }
    }

    override val descriptor: SerialDescriptor = buildClassSerialDescriptor("multiplexed") {
        serializers.forEachIndexed { index, serializer ->
            element(index.toString(), serializer.descriptor, isOptional = true)
        }
    }

    override fun serialize(encoder: Encoder, value: MultiplexedOutput<E>) {
        encoder.encodeStructure(descriptor) {
            encodeSerializableElement(descriptor, value.muxIndex, serializers[value.muxIndex], value.output)
        }
    }
}

fun <T> nullSerializer(): KSerializer<T?> = String.serializer().nullable as KSerializer<T?>
