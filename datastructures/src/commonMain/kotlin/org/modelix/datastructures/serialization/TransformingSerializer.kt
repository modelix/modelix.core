package org.modelix.datastructures.serialization

import kotlinx.serialization.KSerializer
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder

abstract class TransformingSerializer<ActualT, SerializedT>(
    val actualSerializer: KSerializer<SerializedT>,
) : KSerializer<ActualT> {
    override val descriptor: SerialDescriptor get() = actualSerializer.descriptor

    abstract fun convertToSerialized(value: ActualT): SerializedT
    abstract fun convertFromSerialized(value: SerializedT): ActualT

    override fun serialize(encoder: Encoder, value: ActualT) {
        actualSerializer.serialize(encoder, convertToSerialized(value))
    }

    override fun deserialize(decoder: Decoder): ActualT {
        return convertFromSerialized(actualSerializer.deserialize(decoder))
    }
}
