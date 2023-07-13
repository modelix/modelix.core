package org.modelix.modelql.core

import kotlinx.serialization.KSerializer
import kotlinx.serialization.builtins.NothingSerializer
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
    val serializers: List<KSerializer<IStepOutput<E>>>
) : KSerializer<MultiplexedOutput<E>> {
    override fun deserialize(decoder: Decoder): MultiplexedOutput<E> {
        try {
            var muxIndex: Int? = null
            var caseOutput: IStepOutput<E>? = null

            decoder.decodeStructure(descriptor) {
                while (true) {
                    val i = decodeElementIndex(descriptor)
                    if (i == CompositeDecoder.DECODE_DONE) break
                    when (i) {
                        0 -> muxIndex = decodeIntElement(descriptor, i)
                        1 -> {
                            val caseSerializer =
                                getCaseSerializer(muxIndex ?: throw IllegalStateException("muxIndex expected first"))
                            caseOutput = decodeSerializableElement(descriptor, i, caseSerializer)
                        }
                        else -> throw RuntimeException("Unexpected element $i")
                    }
                }
            }
            return MultiplexedOutput(
                muxIndex ?: throw IllegalStateException("muxIndex missing"),
                caseOutput ?: throw IllegalStateException("output missing")
            )
        } catch (ex: Exception) {
            throw RuntimeException("Deserialization failed for step $owner", ex)
        }
    }

    private fun getCaseSerializer(caseIndex: Int) = serializers[caseIndex]

    override val descriptor: SerialDescriptor = buildClassSerialDescriptor("whenStepOutput") {
        element("muxIndex", indexSerializer.descriptor, isOptional = false)
        element("output", dummySerializer.descriptor, isOptional = false)
    }

    override fun serialize(encoder: Encoder, value: MultiplexedOutput<E>) {
        encoder.encodeStructure(descriptor) {
            encodeIntElement(descriptor, 0, value.muxIndex)
            encodeSerializableElement(descriptor, 1, getCaseSerializer(value.muxIndex), value.output)
        }
    }

    companion object {
        private val indexSerializer = Int.serializer()
        private val dummySerializer = NothingSerializer()
    }
}
