package org.modelix.modelql.core

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.KSerializer
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder

/**
 * Can carry some additional data required for processing the result on the client side.
 */
interface IStepOutput<out E> {
    val value: E
}

typealias StepFlow<E> = Flow<IStepOutput<E>>
val <T> Flow<IStepOutput<T>>.value: Flow<T> get() = map { it.value }
fun <T> Flow<T>.asStepFlow(): StepFlow<T> = map { SimpleStepOutput(it) }

data class SimpleStepOutput<out E>(override val value: E) : IStepOutput<E>

data class SimpleStepOutputSerializer<E>(val valueSerializer: KSerializer<E>) : KSerializer<SimpleStepOutput<E>> {
    init {
        require(valueSerializer !is SimpleStepOutputSerializer<*>)
        require(valueSerializer !is ZipOutputSerializer<*, *>)
    }
    override fun deserialize(decoder: Decoder): SimpleStepOutput<E> {
        return SimpleStepOutput(decoder.decodeSerializableValue(valueSerializer))
    }

    override val descriptor: SerialDescriptor
        get() = valueSerializer.descriptor

    override fun serialize(encoder: Encoder, value: SimpleStepOutput<E>) {
        encoder.encodeSerializableValue(valueSerializer, value.value)
    }
}

fun <T> KSerializer<T>.stepOutputSerializer(): SimpleStepOutputSerializer<T> = SimpleStepOutputSerializer(this)

fun <T> T.asStepOutput(): IStepOutput<T> = SimpleStepOutput(this)
