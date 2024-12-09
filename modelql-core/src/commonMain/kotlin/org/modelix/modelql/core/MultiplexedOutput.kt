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

data class MultiplexedOutput<out E>(val muxIndex: Int, val output: IStepOutput<E>) : IStepOutput<E> {
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
